package com.campus.competition.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campus.competition.common.BusinessException;
import com.campus.competition.config.DiscoveryProperties;
import com.campus.competition.config.LlmProperties;
import com.campus.competition.dto.DiscoveryJobReq;
import com.campus.competition.entity.CompetitionDiscoveryJob;
import com.campus.competition.mapper.CompetitionDiscoveryJobMapper;
import com.campus.competition.vo.DiscoveryJobVO;
import com.campus.competition.vo.DiscoveryStatusVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Semaphore;

/**
 * 管理员近期赛事发现：搜索 → 受限抓取 → 证据提取 → 草稿。
 * 搜索只负责找网页；没有取回正文就不会创建“已抓取”草稿。人工 URL/文本导入始终可用。
 */
@Service
public class CompetitionDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(CompetitionDiscoveryService.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CompetitionDiscoveryJobMapper jobMapper;
    private final CompetitionSearchProvider searchProvider;
    private final WebContentFetcher fetcher;
    private final CompetitionExtractionService extractionService;
    private final CompetitionDraftService draftService;
    private final DiscoveryProperties properties;
    private final LlmProperties llmProperties;
    private final UrlSafetyPolicy urlSafetyPolicy;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final TaskExecutor executor;
    private final Semaphore semaphore;

    public CompetitionDiscoveryService(CompetitionDiscoveryJobMapper jobMapper,
                                       CompetitionSearchProvider searchProvider,
                                       WebContentFetcher fetcher,
                                       CompetitionExtractionService extractionService,
                                       CompetitionDraftService draftService,
                                       DiscoveryProperties properties,
                                       LlmProperties llmProperties,
                                       UrlSafetyPolicy urlSafetyPolicy,
                                       ObjectMapper objectMapper,
                                       Clock clock,
                                       @Qualifier("discoveryExecutor") TaskExecutor executor) {
        this.jobMapper = jobMapper;
        this.searchProvider = searchProvider;
        this.fetcher = fetcher;
        this.extractionService = extractionService;
        this.draftService = draftService;
        this.properties = properties;
        this.llmProperties = llmProperties;
        this.urlSafetyPolicy = urlSafetyPolicy;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.executor = executor;
        this.semaphore = new Semaphore(Math.max(1, properties.getJob().getConcurrency()));
    }

    public Long createJob(Long adminId, DiscoveryJobReq req) {
        DiscoveryJobReq request = validate(req);
        if ("SEARCH".equals(request.getMode()) && !searchProvider.isConfigured()) {
            throw new BusinessException(503, searchProvider.configStatus() + "；仍可使用 URL 或粘贴公告文本导入");
        }
        CompetitionDiscoveryJob job = new CompetitionDiscoveryJob();
        job.setAdminId(adminId);
        job.setMode(request.getMode());
        job.setRequestJson(json(request));
        job.setStatus("QUEUED");
        job.setMessage("已排队");
        job.setCreatedAt(LocalDateTime.now(clock));
        jobMapper.insert(job);
        try {
            executor.execute(() -> execute(job.getId(), request, adminId));
        } catch (Exception rejected) {
            finish(job.getId(), "FAILED", "当前发现任务较多，请稍后重试", Map.of());
            throw new BusinessException(503, "当前发现任务较多，请稍后重试");
        }
        return job.getId();
    }

    public DiscoveryJobVO get(Long id) {
        CompetitionDiscoveryJob job = jobMapper.selectById(id);
        if (job == null) throw new BusinessException(404, "发现作业不存在");
        DiscoveryJobVO vo = new DiscoveryJobVO();
        vo.setId(job.getId());
        vo.setMode(job.getMode());
        vo.setStatus(job.getStatus());
        vo.setMessage(job.getMessage());
        vo.setCreatedAt(job.getCreatedAt());
        vo.setStartedAt(job.getStartedAt());
        vo.setFinishedAt(job.getFinishedAt());
        vo.setRequest(readMap(job.getRequestJson()));
        vo.setResult(readMap(job.getResultJson()));
        return vo;
    }

    public DiscoveryStatusVO status() {
        DiscoveryStatusVO vo = new DiscoveryStatusVO();
        vo.setSearchConfigured(searchProvider.isConfigured());
        vo.setSearchProvider(searchProvider.name());
        vo.setSearchStatus(searchProvider.configStatus());
        vo.setModelConfigured(llmProperties.isConfigured());
        vo.setModelStatus(llmProperties.configStatus());
        vo.setFetchTimeoutSeconds(properties.getFetch().getTimeoutSeconds());
        vo.setMaxResults(properties.getJob().getMaxResults());
        vo.setJobTimeoutSeconds(properties.getJob().getTimeoutSeconds());
        return vo;
    }

    // ==================== 作业执行 ====================

    private void execute(Long jobId, DiscoveryJobReq request, Long adminId) {
        if (!semaphore.tryAcquire()) {
            finish(jobId, "FAILED", "当前已有发现任务运行，请稍后再试", Map.of());
            return;
        }
        try {
            int started = jobMapper.update(null, new LambdaUpdateWrapper<CompetitionDiscoveryJob>()
                    .eq(CompetitionDiscoveryJob::getId, jobId)
                    .eq(CompetitionDiscoveryJob::getStatus, "QUEUED")
                    .set(CompetitionDiscoveryJob::getStatus, "RUNNING")
                    .set(CompetitionDiscoveryJob::getStartedAt, LocalDateTime.now(clock)));
            if (started == 0) return;

            JobResult result = new JobResult();
            long deadlineNanos = System.nanoTime()
                    + java.util.concurrent.TimeUnit.SECONDS.toNanos(
                    Math.max(1, properties.getJob().getTimeoutSeconds()));
            if ("SEARCH".equals(request.getMode())) {
                searchAndExtract(request, adminId, result, deadlineNanos);
            } else if ("URL".equals(request.getMode())) {
                urlAndExtract(request, adminId, result, deadlineNanos);
            } else {
                textAndExtract(request, adminId, result);
            }
            finishWith(result, jobId);
        } catch (SearchException e) {
            log.warn("发现作业 {} 搜索失败: {} {}", jobId, e.getCode(), e.getMessage());
            finish(jobId, "FAILED", e.getMessage(), Map.of("errorCode", e.getCode()));
        } catch (FetchException e) {
            log.warn("发现作业 {} 抓取失败: {} {}", jobId, e.getCode(), e.getMessage());
            finish(jobId, "FAILED", e.getMessage(), Map.of("errorCode", e.getCode()));
        } catch (Exception e) {
            log.error("发现作业 {} 执行失败", jobId, e);
            finish(jobId, "FAILED", "作业执行失败，请查看服务日志后重试", Map.of());
        } finally {
            semaphore.release();
        }
    }

    private void searchAndExtract(DiscoveryJobReq request, Long adminId, JobResult result,
                                  long deadlineNanos) throws SearchException {
        String query = buildQuery(request);
        result.searchQuery = query;
        result.provider = searchProvider.name();
        int max = Math.min(request.getMaxResults() == null ? properties.getJob().getMaxResults() : request.getMaxResults(),
                properties.getJob().getMaxResults());
        List<SearchHit> hits = searchProvider.search(query, max);
        if (hits.isEmpty()) {
            throw new SearchException("SEARCH_EMPTY", "没有搜索到结果，可更换关键词或改用 URL/文本导入");
        }
        for (SearchHit hit : hits) {
            // 单次 socket 超时不等于作业总时限：到点后不再开始新的抓取和提取。
            if (expired(deadlineNanos)) {
                result.truncated = true;
                addFailure(result, hit.getUrl(), "JOB_TIMEOUT",
                        "作业超过 " + properties.getJob().getTimeoutSeconds()
                                + " 秒总时限，未处理的结果已跳过；可缩小 maxResults 后重试");
                break;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("title", hit.getTitle());
            item.put("url", hit.getUrl());
            result.hits.add(item);
            try {
                FetchedPage page = fetcher.fetch(hit.getUrl());
                if (expired(deadlineNanos)) {
                    result.truncated = true;
                    addFailure(result, hit.getUrl(), "JOB_TIMEOUT",
                            "抓取完成时已超过作业总时限，未执行提取");
                    break;
                }
                extractAndCreate(page.getFinalUrl(), page.getTitle(), page.getText(),
                        page.getSourceType(), true, result, adminId);
            } catch (FetchException e) {
                addFailure(result, hit.getUrl(), e.getCode(), e.getMessage());
            }
        }
    }

    private void urlAndExtract(DiscoveryJobReq request, Long adminId, JobResult result,
                               long deadlineNanos) throws FetchException {
        FetchedPage page = fetcher.fetch(request.getUrl());
        if (expired(deadlineNanos)) {
            result.truncated = true;
            addFailure(result, request.getUrl(), "JOB_TIMEOUT", "抓取完成时已超过作业总时限，未执行提取");
            return;
        }
        result.hits.add(Map.of("title", page.getTitle() == null ? "" : page.getTitle(),
                "url", page.getFinalUrl() == null ? request.getUrl() : page.getFinalUrl()));
        extractAndCreate(page.getFinalUrl(), page.getTitle(), page.getText(),
                page.getSourceType(), true, result, adminId);
    }

    private boolean expired(long deadlineNanos) {
        return System.nanoTime() - deadlineNanos > 0;
    }

    private void textAndExtract(DiscoveryJobReq request, Long adminId, JobResult result) {
        String title = request.getPageTitle() == null || request.getPageTitle().isBlank()
                ? "管理员粘贴的公告文本" : request.getPageTitle().trim();
        result.hits.add(Map.of("title", title, "url", ""));
        extractAndCreate(null, title, request.getText(), "MANUAL_TEXT", false, result, adminId);
    }

    private void extractAndCreate(String sourceUrl, String pageTitle, String text, String sourceType,
                                  boolean fetched, JobResult result, Long adminId) {
        CompetitionExtraction extraction = extractionService.extract(text, pageTitle);
        CompetitionDraftService.DraftCreateResult created = draftService.createFromExtraction(
                extraction, sourceUrl, pageTitle, sourceType,
                extraction.getMode(), text, fetched, adminId);
        if (created.draft == null) {
            Map<String, Object> duplicate = new LinkedHashMap<>();
            duplicate.put("duplicateType", created.duplicateType);
            duplicate.put("duplicateId", created.duplicateId);
            duplicate.put("url", sourceUrl);
            duplicate.put("name", extraction.getName());
            duplicate.put("message", "COMPETITION".equals(created.duplicateType)
                    ? "已存在相同赛事，未创建草稿" : "已存在相同来源草稿，未重复创建");
            result.duplicates.add(duplicate);
            return;
        }
        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("draftId", created.draft.getId());
        draft.put("name", created.draft.getName());
        draft.put("status", created.draft.getStatus());
        draft.put("extractionMode", created.draft.getExtractionMode());
        draft.put("sourceType", created.draft.getSourceType());
        draft.put("sourceUrl", sourceUrl);
        draft.put("missing", readList(created.draft.getMissingFields()));
        draft.put("conflicts", readList(created.draft.getConflicts()));
        draft.put("fallbackReason", extraction.getFallbackReason());
        result.drafts.add(draft);
    }

    private void finishWith(JobResult result, Long jobId) {
        String status;
        String message;
        if (!result.drafts.isEmpty() && result.failures.isEmpty() && result.duplicates.isEmpty()) {
            status = "SUCCEEDED";
            message = "已创建 " + result.drafts.size() + " 份草稿，等待管理员审核";
        } else if (!result.drafts.isEmpty()) {
            status = "PARTIAL";
            message = "已创建 " + result.drafts.size() + " 份草稿；"
                    + result.failures.size() + " 条抓取/提取失败，"
                    + result.duplicates.size() + " 条重复";
        } else {
            status = "FAILED";
            if (!result.failures.isEmpty()) {
                message = "未创建草稿：" + result.failures.get(0).get("message");
            } else if (!result.duplicates.isEmpty()) {
                message = "未创建草稿：" + result.duplicates.get(0).get("message");
            } else {
                message = "未创建草稿：正文没有可提取的赛事信息";
            }
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("searchQuery", result.searchQuery);
        payload.put("provider", result.provider);
        payload.put("truncated", result.truncated);
        payload.put("timeoutSeconds", properties.getJob().getTimeoutSeconds());
        payload.put("hits", result.hits);
        payload.put("drafts", result.drafts);
        payload.put("duplicates", result.duplicates);
        payload.put("failures", result.failures);
        finish(jobId, status, message, payload);
    }

    private void finish(Long jobId, String status, String message, Map<String, Object> result) {
        CompetitionDiscoveryJob job = jobMapper.selectById(jobId);
        if (job == null) return;
        job.setStatus(status);
        job.setMessage(limit(message, 1000));
        job.setResultJson(json(result));
        job.setFinishedAt(LocalDateTime.now(clock));
        jobMapper.updateById(job);
    }

    private void addFailure(JobResult result, String url, String code, String message) {
        Map<String, Object> failure = new LinkedHashMap<>();
        failure.put("url", url);
        failure.put("code", code);
        failure.put("message", message);
        result.failures.add(failure);
    }

    // ==================== 校验与工具 ====================

    private DiscoveryJobReq validate(DiscoveryJobReq req) {
        if (req == null) throw new BusinessException(400, "缺少作业参数");
        String mode = req.getMode() == null ? "" : req.getMode().toUpperCase(Locale.ROOT);
        if (!List.of("SEARCH", "URL", "TEXT").contains(mode)) {
            throw new BusinessException(400, "mode 只能是 SEARCH、URL 或 TEXT");
        }
        DiscoveryJobReq value = new DiscoveryJobReq();
        value.setMode(mode);
        value.setKeyword(limit(trim(req.getKeyword()), 100));
        value.setDirection(limit(trim(req.getDirection()), 50));
        value.setRegion(limit(trim(req.getRegion()), 50));
        value.setPageTitle(limit(trim(req.getPageTitle()), 200));
        value.setUrl(trim(req.getUrl()));
        value.setText(trim(req.getText()));
        int window = req.getWindowDays() == null ? 90 : req.getWindowDays();
        if (window < 1 || window > 365) throw new BusinessException(400, "时间窗口需在 1 到 365 天之间");
        value.setWindowDays(window);
        int max = req.getMaxResults() == null ? properties.getJob().getMaxResults() : req.getMaxResults();
        value.setMaxResults(Math.max(1, Math.min(max, properties.getJob().getMaxResults())));

        if ("SEARCH".equals(mode)) {
            if ((value.getKeyword() == null || value.getKeyword().isBlank())
                    && (value.getDirection() == null || value.getDirection().isBlank())) {
                throw new BusinessException(400, "搜索至少需要关键词或方向");
            }
        } else if ("URL".equals(mode)) {
            if (value.getUrl() == null) throw new BusinessException(400, "请填写公告 URL");
            try {
                urlSafetyPolicy.validate(value.getUrl());
            } catch (FetchException e) {
                throw new BusinessException(400, e.getMessage());
            }
        } else {
            if (value.getText() == null || value.getText().length() < 20) {
                throw new BusinessException(400, "请粘贴至少 20 字的公告正文");
            }
            if (value.getText().length() > 20000) {
                throw new BusinessException(400, "粘贴正文最长 20000 字");
            }
        }
        return value;
    }

    private String buildQuery(DiscoveryJobReq req) {
        List<String> parts = new ArrayList<>();
        if (req.getKeyword() != null && !req.getKeyword().isBlank()) parts.add(req.getKeyword());
        if (req.getDirection() != null && !req.getDirection().isBlank()) parts.add(req.getDirection());
        if (req.getRegion() != null && !req.getRegion().isBlank()) parts.add(req.getRegion());
        parts.add("竞赛");
        parts.add("报名");
        String query = String.join(" ", parts);
        return limit(query, 200);
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) return new LinkedHashMap<>();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() { });
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private List<String> readList(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() { });
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private String limit(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static final class JobResult {
        String searchQuery;
        String provider;
        boolean truncated;
        final List<Map<String, Object>> hits = new ArrayList<>();
        final List<Map<String, Object>> drafts = new ArrayList<>();
        final List<Map<String, Object>> duplicates = new ArrayList<>();
        final List<Map<String, Object>> failures = new ArrayList<>();
    }
}
