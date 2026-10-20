package com.campus.competition.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.competition.common.BusinessException;
import com.campus.competition.common.PageVO;
import com.campus.competition.dto.CompetitionReq;
import com.campus.competition.dto.DraftUpdateReq;
import com.campus.competition.entity.Competition;
import com.campus.competition.entity.CompetitionDraft;
import com.campus.competition.entity.CompetitionDraftReview;
import com.campus.competition.entity.CompetitionSource;
import com.campus.competition.mapper.CompetitionDraftMapper;
import com.campus.competition.mapper.CompetitionDraftReviewMapper;
import com.campus.competition.mapper.CompetitionMapper;
import com.campus.competition.mapper.CompetitionSourceMapper;
import com.campus.competition.support.CompetitionIdentity;
import com.campus.competition.support.TextSignals;
import com.campus.competition.vo.CompetitionDraftVO;
import com.campus.competition.vo.DuplicateCandidateVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 赛事发现草稿：创建（含来源与证据）、编辑、审核、重复检测、幂等发布。
 * 学生端只读取 competition 表，草稿表永远不直接暴露给学生。
 */
@Service
public class CompetitionDraftService {

    private static final Logger log = LoggerFactory.getLogger(CompetitionDraftService.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> STATUSES = Set.of("DRAFT", "READY", "PUBLISHED", "REJECTED");

    private final CompetitionDraftMapper draftMapper;
    private final CompetitionSourceMapper sourceMapper;
    private final CompetitionDraftReviewMapper reviewMapper;
    private final CompetitionMapper competitionMapper;
    private final CompetitionService competitionService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public CompetitionDraftService(CompetitionDraftMapper draftMapper, CompetitionSourceMapper sourceMapper,
                                   CompetitionDraftReviewMapper reviewMapper, CompetitionMapper competitionMapper,
                                   CompetitionService competitionService, ObjectMapper objectMapper, Clock clock) {
        this.draftMapper = draftMapper;
        this.sourceMapper = sourceMapper;
        this.reviewMapper = reviewMapper;
        this.competitionMapper = competitionMapper;
        this.competitionService = competitionService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public PageVO<CompetitionDraft> list(String status, long page, long size) {
        if (status != null && !status.isBlank() && !STATUSES.contains(status)) {
            throw new BusinessException(400, "未知草稿状态");
        }
        LambdaQueryWrapper<CompetitionDraft> query = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) query.eq(CompetitionDraft::getStatus, status);
        query.orderByDesc(CompetitionDraft::getUpdatedAt);
        Page<CompetitionDraft> result = draftMapper.selectPage(
                new Page<>(Math.max(1, page), Math.max(1, Math.min(size, 50))), query);
        return PageVO.from(result);
    }

    public CompetitionDraftVO detail(Long id) {
        CompetitionDraft draft = draftMapper.selectById(id);
        if (draft == null) throw new BusinessException(404, "草稿不存在");
        return toVO(draft);
    }

    @Transactional
    public CompetitionDraftVO update(Long id, DraftUpdateReq req, Long adminId) {
        if (req == null) throw new BusinessException(400, "缺少草稿内容");
        CompetitionDraft draft = draftMapper.selectForUpdate(id);
        if (draft == null) throw new BusinessException(404, "草稿不存在");
        if (req.getVersion() == null || !req.getVersion().equals(draft.getVersion())) {
            throw new BusinessException(409, "草稿已被其他管理员修改，请刷新后重试");
        }
        if ("PUBLISHED".equals(draft.getStatus())) {
            throw new BusinessException(409, "已发布草稿不可直接修改；如赛事有误，请明确编辑原赛事");
        }
        String action = req.getAction() == null ? "SAVE" : req.getAction().toUpperCase(Locale.ROOT);
        if (!Set.of("SAVE", "MARK_READY", "REJECT", "REOPEN").contains(action)) {
            throw new BusinessException(400, "未知审核动作");
        }
        String from = draft.getStatus();
        String changeSummary = null;

        if ("REJECT".equals(action)) {
            if (req.getReviewNote() == null || req.getReviewNote().isBlank()) {
                throw new BusinessException(400, "拒绝草稿必须填写原因");
            }
            draft.setStatus("REJECTED");
            draft.setReviewedVersion(null);
            draft.setReviewNote(limit(req.getReviewNote(), 1000));
        } else if ("REOPEN".equals(action)) {
            if (!"REJECTED".equals(draft.getStatus())) {
                throw new BusinessException(400, "只有已拒绝草稿可以重新打开");
            }
            draft.setStatus("DRAFT");
            draft.setReviewedVersion(null);
            if (req.getReviewNote() != null) draft.setReviewNote(limit(req.getReviewNote(), 1000));
        } else {
            if ("REJECTED".equals(from)) {
                throw new BusinessException(409, "草稿已被拒绝，请先重新打开再编辑");
            }
            Map<String, String> before = contentSnapshot(draft);
            applyFields(draft, req);
            Map<String, String> after = contentSnapshot(draft);
            List<String> changed = diff(before, after);
            changeSummary = describeChanges(before, after, changed);

            // 身份字段变化后必须重新查重，不能沿用创建草稿时的旧结论。
            if (changed.stream().anyMatch(IDENTITY_FIELDS::contains)) {
                recheckDuplicates(draft);
                draft.setDuplicateAcknowledged(false);
            }
            if (!changed.isEmpty()) {
                if (changed.stream().anyMatch(DATE_FIELDS::contains)) {
                    draft.setDateInterpretationConfirmed(false);
                }
                if ("READY".equals(from)) {
                    // 审核后改内容必须重新审核，避免旧页面直接发布已变更的草稿。
                    draft.setStatus("DRAFT");
                    draft.setReviewedVersion(null);
                    changeSummary = changeSummary + "；内容变更使原审核失效，已退回草稿待重新审核";
                }
            }
            recompute(draft);
            if ("MARK_READY".equals(action)) {
                List<String> blockers = blockers(draft);
                if (!blockers.isEmpty()) {
                    throw new BusinessException(400, "无法标记待发布：" + String.join("；", blockers));
                }
                draft.setStatus("READY");
            }
        }

        int nextVersion = draft.getVersion() + 1;
        if ("MARK_READY".equals(action)) {
            // 记录“审核生效的版本号”，发布时必须与当前版本一致。
            draft.setReviewedVersion(nextVersion);
        }
        draft.setVersion(nextVersion);
        draft.setUpdatedBy(adminId);
        draft.setUpdatedAt(LocalDateTime.now(clock));
        draftMapper.updateById(draft);
        review(draft.getId(), action, from, draft.getStatus(), adminId,
                req.getReviewNote(), changeSummary, nextVersion);
        return toVO(draft);
    }

    /**
     * 幂等发布：行锁 + 审核版本校验 + 唯一 publish_key + 事务内重新查重。
     * 重复点击或并发发布同一草稿最多创建一条竞赛；不同草稿指向同一赛事实例时阻止后发布的一份。
     */
    @Transactional
    public Long publish(Long id, Integer expectedVersion, Long adminId) {
        CompetitionDraft draft = draftMapper.selectForUpdate(id);
        if (draft == null) throw new BusinessException(404, "草稿不存在");
        if ("PUBLISHED".equals(draft.getStatus()) && draft.getPublishedCompetitionId() != null) {
            return draft.getPublishedCompetitionId();
        }
        // 未发布草稿必须携带期望版本：旧页面/旧链接不能直接发布已变更的草稿。
        if (expectedVersion == null) {
            throw new BusinessException(400, "发布必须携带草稿当前版本号（?version=），防止旧页面误发布");
        }
        if (!expectedVersion.equals(draft.getVersion())) {
            throw new BusinessException(409, "草稿已被其他管理员修改，请刷新后重新审核");
        }
        if (!"READY".equals(draft.getStatus())) {
            throw new BusinessException(409, "只有待发布草稿可以发布，请先完成审核");
        }
        if (draft.getReviewedVersion() == null || !draft.getReviewedVersion().equals(draft.getVersion())) {
            throw new BusinessException(409, "草稿在审核后已被修改，请重新标记待发布");
        }
        List<String> blockers = blockers(draft);
        if (!blockers.isEmpty()) {
            throw new BusinessException(400, "无法发布：" + String.join("；", blockers));
        }
        // 发布事务内重新查重：覆盖“另一份草稿刚刚发布了同一赛事”的窗口。
        DuplicateCheck recheck = detectDuplicates(draft.getName(), draft.getOrganizer(),
                draft.getCompetitionYear(), draft.getEdition(), draft.getTrack(),
                draft.getNormalizedUrl(), draft.getId());
        if (recheck.exactType != null) {
            throw new BusinessException(409, "已存在同一赛事的现有记录（" + recheck.exactType
                    + " ID " + recheck.exactId + "），不能重复发布；如确为新一届，请补全年份/届次/赛道后重新审核");
        }

        CompetitionReq req = new CompetitionReq();
        req.setName(draft.getName());
        req.setOrganizer(draft.getOrganizer());
        req.setLevel(draft.getLevel());
        req.setSignupStart(format(draft.getSignupStart()));
        req.setSignupEnd(format(draft.getSignupEnd()));
        req.setStartTime(format(draft.getStartTime()));
        req.setDescription(draft.getDescription());
        req.setTags(draft.getTags());
        req.setEligibilityText(draft.getEligibilityText());
        req.setYear(draft.getCompetitionYear());
        req.setEdition(draft.getEdition());
        req.setTrack(draft.getTrack());
        req.setSourceUrl(draft.getSourceUrl());
        req.setSourceType(draft.getSourceType());
        Competition competition;
        try {
            competition = competitionService.create(req);
        } catch (BusinessException conflict) {
            if (conflict.getCode() == 409) {
                throw new BusinessException(409, "并发发布被数据库唯一键拦截：" + conflict.getMessage());
            }
            throw conflict;
        }

        draft.setStatus("PUBLISHED");
        draft.setPublishedCompetitionId(competition.getId());
        draft.setPublishKey("draft:" + draft.getId());
        draft.setPublishedAt(LocalDateTime.now(clock));
        draft.setVersion(draft.getVersion() + 1);
        draft.setUpdatedBy(adminId);
        draft.setUpdatedAt(LocalDateTime.now(clock));
        try {
            draftMapper.updateById(draft);
        } catch (DuplicateKeyException duplicate) {
            // 唯一键兜底：回滚本次竞赛插入，提示重试；已发布草稿重试会返回原 ID。
            org.springframework.transaction.interceptor.TransactionAspectSupport
                    .currentTransactionStatus().setRollbackOnly();
            throw new BusinessException(409, "发布冲突，请重试；已发布的草稿会返回原竞赛 ID");
        }

        for (CompetitionSource source : sources(id)) {
            source.setCompetitionId(competition.getId());
            sourceMapper.updateById(source);
        }
        review(id, "PUBLISH", "READY", "PUBLISHED", adminId, null, null, draft.getVersion());
        log.info("赛事草稿 {} 已发布为竞赛 {}", id, competition.getId());
        return competition.getId();
    }

    // ==================== 发现作业创建草稿 ====================

    public static final class DraftCreateResult {
        public CompetitionDraft draft;
        public String duplicateType;
        public Long duplicateId;
        public List<DuplicateCandidateVO> duplicates = new ArrayList<>();
    }

    @Transactional
    public DraftCreateResult createFromExtraction(CompetitionExtraction extraction, String sourceUrl,
                                                  String pageTitle, String sourceType,
                                                  String extractionMode, String rawText,
                                                  boolean fetched, Long adminId) {
        DraftCreateResult result = new DraftCreateResult();
        DuplicateCheck check = detectDuplicates(extraction.getName(), extraction.getOrganizer(),
                extraction.getYear(), extraction.getEdition(), extraction.getTrack(),
                TextSignals.normalizeUrl(sourceUrl), null);
        if (check.exactType != null) {
            result.duplicateType = check.exactType;
            result.duplicateId = check.exactId;
            result.duplicates = check.suspects;
            return result;
        }

        LocalDateTime now = LocalDateTime.now(clock);
        CompetitionDraft draft = new CompetitionDraft();
        draft.setName(extraction.getName());
        draft.setOrganizer(extraction.getOrganizer());
        draft.setCompetitionYear(extraction.getYear());
        draft.setEdition(extraction.getEdition());
        draft.setTrack(extraction.getTrack());
        draft.setLevel(extraction.getLevel());
        draft.setDescription(extraction.getDescription());
        draft.setTags(extraction.getTags() == null || extraction.getTags().isEmpty()
                ? null : String.join(",", extraction.getTags()));
        draft.setEligibilityText(extraction.getEligibilityText());
        draft.setSignupStart(extraction.getSignupStart());
        draft.setSignupEnd(extraction.getSignupEnd());
        draft.setStartTime(extraction.getStartTime());
        draft.setSignupEndPrecision(extraction.getSignupEndPrecision());
        draft.setDateNote(extraction.getDateNote());
        draft.setDateInterpretationConfirmed(false);
        draft.setSourceUrl(sourceUrl);
        draft.setNormalizedUrl(TextSignals.normalizeUrl(sourceUrl));
        draft.setPageTitle(pageTitle);
        draft.setSourceOrg(extraction.getOrganizer());
        draft.setFetchedAt(fetched ? now : null);
        draft.setPublishedAt(null);
        draft.setSourceType(sourceType);
        draft.setExtractionMode(extractionMode);
        draft.setStatus("DRAFT");
        draft.setVersion(0);
        draft.setDuplicateAcknowledged(false);
        draft.setDuplicateCandidates(json(check.suspects));
        draft.setMissingFields(json(extraction.getMissing()));
        draft.setConflicts(json(extraction.getConflicts()));
        draft.setWarnings(json(extraction.getWarnings()));
        draft.setCreatedBy(adminId);
        draft.setUpdatedBy(adminId);
        draft.setCreatedAt(now);
        draft.setUpdatedAt(now);
        recompute(draft);
        draftMapper.insert(draft);

        CompetitionSource source = new CompetitionSource();
        source.setDraftId(draft.getId());
        source.setSourceType(sourceType);
        source.setUrl(sourceUrl);
        source.setNormalizedUrl(draft.getNormalizedUrl());
        source.setPageTitle(pageTitle);
        source.setSourceOrg(extraction.getOrganizer());
        source.setPublishedAt(null);
        source.setFetchedAt(fetched ? now : null);
        source.setFetchStatus(fetched ? "FETCHED" : "PROVIDED");
        source.setFetchMessage(fetched ? null : "管理员粘贴文本，系统未抓取网页");
        source.setManual(!fetched);
        source.setRawText(limit(rawText, 60000));
        source.setEvidenceJson(json(extraction.getEvidence()));
        source.setCreatedAt(now);
        sourceMapper.insert(source);

        review(draft.getId(), "CREATE", null, "DRAFT", adminId,
                extraction.getFallbackReason(), null, 0);
        result.draft = draft;
        result.duplicates = check.suspects;
        return result;
    }

    // ==================== 重复检测 ====================

    private static final class DuplicateCheck {
        String exactType;
        Long exactId;
        List<DuplicateCandidateVO> suspects = new ArrayList<>();
    }

    DuplicateCheck detectDuplicates(String name, String organizer, Integer year, String edition, String track,
                                    String normalizedUrl, Long excludeDraftId) {
        DuplicateCheck check = new DuplicateCheck();
        if (name == null || name.isBlank()) return check;
        List<Competition> competitions = competitionMapper.selectList(null);
        List<CompetitionDraft> drafts = draftMapper.selectList(
                new LambdaQueryWrapper<CompetitionDraft>()
                        .ne(excludeDraftId != null, CompetitionDraft::getId, excludeDraftId)
                        .ne(CompetitionDraft::getStatus, "REJECTED"));

        for (Competition competition : competitions) {
            CompetitionIdentity.Match match = CompetitionIdentity.compare(name, organizer, year, edition, track,
                    normalizedUrl, competition.getName(), competition.getOrganizer(),
                    competition.getCompetitionYear(), competition.getEdition(), competition.getTrack(),
                    TextSignals.normalizeUrl(competition.getSourceUrl()));
            if (match.verdict() == CompetitionIdentity.Verdict.EXACT) {
                check.exactType = "COMPETITION";
                check.exactId = competition.getId();
                check.suspects.add(candidate("COMPETITION", competition.getId(), competition.getName(),
                        competition.getOrganizer(), competition.getCompetitionYear(), competition.getEdition(),
                        competition.getTrack(), competition.getSourceUrl(), 100, "同一实例：" + match.reason()));
                return check;
            }
            addSuspect(check, match, "COMPETITION", competition.getId(), competition.getName(),
                    competition.getOrganizer(), year, competition.getEdition(), competition.getTrack(),
                    competition.getSourceUrl(), competition.getCompetitionYear(),
                    sameSource(normalizedUrl, competition.getSourceUrl()));
        }

        for (CompetitionDraft draft : drafts) {
            CompetitionIdentity.Match match = CompetitionIdentity.compare(name, organizer, year, edition, track,
                    normalizedUrl, draft.getName(), draft.getOrganizer(), draft.getCompetitionYear(),
                    draft.getEdition(), draft.getTrack(), draft.getNormalizedUrl());
            if (match.verdict() == CompetitionIdentity.Verdict.EXACT) {
                check.exactType = "DRAFT";
                check.exactId = draft.getId();
                check.suspects.add(candidate("DRAFT", draft.getId(), draft.getName(), draft.getOrganizer(),
                        draft.getCompetitionYear(), draft.getEdition(), draft.getTrack(), draft.getSourceUrl(),
                        100, "同一实例：" + match.reason()));
                return check;
            }
            addSuspect(check, match, "DRAFT", draft.getId(), draft.getName(), draft.getOrganizer(),
                    year, draft.getEdition(), draft.getTrack(), draft.getSourceUrl(),
                    draft.getCompetitionYear(), sameSource(normalizedUrl, draft.getNormalizedUrl()));
        }

        Map<String, DuplicateCandidateVO> unique = new LinkedHashMap<>();
        for (DuplicateCandidateVO candidate : check.suspects) {
            unique.putIfAbsent(candidate.getType() + ":" + candidate.getId(), candidate);
        }
        check.suspects = unique.values().stream().limit(6).collect(Collectors.toList());
        return check;
    }

    /** 非“同一实例”的匹配只做提示；差异确认不能放行 EXACT 的情况。 */
    private void addSuspect(DuplicateCheck check, CompetitionIdentity.Match match, String type, Long id,
                            String name, String organizer, Integer year, String edition, String track,
                            String sourceUrl, Integer otherYear, boolean sameSource) {
        if (match.verdict() == CompetitionIdentity.Verdict.NONE) {
            boolean yearDiffers = year != null && otherYear != null && !year.equals(otherYear);
            if (sameSource) {
                // URL 只是线索：同名与否都要提示，但不据此直接判为同一实例。
                check.suspects.add(candidate(type, id, name, organizer, year, edition, track, sourceUrl,
                        90, "来源 URL 与现有记录相同"
                        + (yearDiffers ? "，但年份不同（" + year + " / " + otherYear + "），可能是新一届"
                        : "，名称写法则不同，请确认是否同一赛事的重复录入")));
                return;
            }
            // 年份明确不同时降低相似度门槛，提示“可能是同一系列的新一届”，但允许审核通过。
            int threshold = yearDiffers ? 60 : 75;
            if (match.similarity() < threshold) return;
            check.suspects.add(candidate(type, id, name, organizer, year, edition, track, sourceUrl,
                    match.similarity(), yearDiffers
                            ? "名称相似但年份不同（" + year + " / " + otherYear + "），可能是同一系列的新一届"
                            : "赛事名称相似，可能是往届、别名或同一赛事的不同通知"));
            return;
        }
        String reason = sameSource && match.reason() != null
                ? "来源 URL 相同；" + match.reason() : match.reason();
        check.suspects.add(candidate(type, id, name, organizer, year, edition, track, sourceUrl,
                match.similarity(), reason));
    }

    private DuplicateCandidateVO candidate(String type, Long id, String name, String organizer,
                                           Integer year, String edition, String track, String sourceUrl,
                                           int similarity, String reason) {
        return new DuplicateCandidateVO(type, id, name, organizer, year, edition, track, sourceUrl,
                similarity, reason);
    }

    private boolean sameSource(String normalizedUrl, String otherUrl) {
        if (normalizedUrl == null || normalizedUrl.isBlank()) return false;
        String other = otherUrl != null && otherUrl.contains("://")
                ? TextSignals.normalizeUrl(otherUrl) : otherUrl;
        return normalizedUrl.equals(other);
    }

    /** 身份字段变化后重新查重，并让旧的“差异确认”失效。 */
    private void recheckDuplicates(CompetitionDraft draft) {
        DuplicateCheck check = detectDuplicates(draft.getName(), draft.getOrganizer(),
                draft.getCompetitionYear(), draft.getEdition(), draft.getTrack(),
                draft.getNormalizedUrl(), draft.getId());
        draft.setDuplicateCandidates(json(check.suspects));
    }

    // ==================== 字段与校验 ====================

    /** 决定“是否同一赛事实例”的字段；变化后必须重新查重。 */
    private static final Set<String> IDENTITY_FIELDS = Set.of("赛事名称", "主办方", "年份", "届次", "赛道");

    /** 影响“按当日 23:59:59 解释”的字段；变化后需重新确认日期解释。 */
    private static final Set<String> DATE_FIELDS = Set.of(
            "报名开始", "报名截止", "开赛时间", "截止精度", "日期说明");

    /** 内容快照只包含用户可改的业务字段，不含审核备注/确认开关。 */
    private Map<String, String> contentSnapshot(CompetitionDraft draft) {
        Map<String, String> snapshot = new LinkedHashMap<>();
        snapshot.put("赛事名称", draft.getName());
        snapshot.put("主办方", draft.getOrganizer());
        snapshot.put("年份", draft.getCompetitionYear() == null ? null : String.valueOf(draft.getCompetitionYear()));
        snapshot.put("届次", draft.getEdition());
        snapshot.put("赛道", draft.getTrack());
        snapshot.put("级别", draft.getLevel());
        snapshot.put("简介", draft.getDescription());
        snapshot.put("方向标签", draft.getTags());
        snapshot.put("参赛条件", draft.getEligibilityText());
        snapshot.put("报名开始", format(draft.getSignupStart()));
        snapshot.put("报名截止", format(draft.getSignupEnd()));
        snapshot.put("开赛时间", format(draft.getStartTime()));
        snapshot.put("截止精度", draft.getSignupEndPrecision());
        snapshot.put("日期说明", draft.getDateNote());
        return snapshot;
    }

    private List<String> diff(Map<String, String> before, Map<String, String> after) {
        List<String> changed = new ArrayList<>();
        for (Map.Entry<String, String> entry : before.entrySet()) {
            if (!Objects.equals(entry.getValue(), after.get(entry.getKey()))) {
                changed.add(entry.getKey());
            }
        }
        return changed;
    }

    /** 记录修改前后值，便于追溯“审核后谁改了什么”。 */
    private String describeChanges(Map<String, String> before, Map<String, String> after, List<String> changed) {
        if (changed.isEmpty()) return null;
        List<String> parts = new ArrayList<>();
        for (String field : changed) {
            parts.add(field + "：" + display(before.get(field)) + " → " + display(after.get(field)));
            if (parts.size() >= 8) break;
        }
        String summary = String.join("；", parts);
        return limit(summary, 1000);
    }

    private String display(String value) {
        return value == null || value.isBlank() ? "（空）" : value;
    }

    private void applyFields(CompetitionDraft draft, DraftUpdateReq req) {
        if (req.getName() != null) draft.setName(blankToNull(req.getName()));
        if (req.getOrganizer() != null) draft.setOrganizer(blankToNull(req.getOrganizer()));
        if (req.getYear() != null) {
            if (req.getYear() == 0) draft.setCompetitionYear(null);
            else if (req.getYear() < 2000 || req.getYear() > 2100) {
                throw new BusinessException(400, "赛事年份需在 2000 到 2100 之间");
            } else draft.setCompetitionYear(req.getYear());
        }
        if (req.getEdition() != null) draft.setEdition(blankToNull(req.getEdition()));
        if (req.getTrack() != null) draft.setTrack(blankToNull(req.getTrack()));
        if (req.getLevel() != null) draft.setLevel(blankToNull(req.getLevel()));
        if (req.getDescription() != null) draft.setDescription(limit(blankToNull(req.getDescription()), 2000));
        if (req.getTags() != null) draft.setTags(limit(blankToNull(req.getTags()), 300));
        if (req.getEligibilityText() != null) draft.setEligibilityText(limit(blankToNull(req.getEligibilityText()), 1000));
        if (req.getDateNote() != null) draft.setDateNote(limit(blankToNull(req.getDateNote()), 500));
        if (req.getConfirmDateInterpretation() != null) {
            draft.setDateInterpretationConfirmed(req.getConfirmDateInterpretation());
        }
        if (req.getDuplicateAcknowledged() != null) {
            draft.setDuplicateAcknowledged(req.getDuplicateAcknowledged());
        }
        if (req.getDuplicateNote() != null) draft.setDuplicateNote(limit(blankToNull(req.getDuplicateNote()), 500));
        if (req.getReviewNote() != null) draft.setReviewNote(limit(blankToNull(req.getReviewNote()), 1000));

        if (req.getSignupStart() != null) {
            draft.setSignupStart(parse(req.getSignupStart(), false, "报名开始"));
        }
        if (req.getSignupEnd() != null) {
            String value = blankToNull(req.getSignupEnd());
            boolean dateOnly = value != null && value.length() <= 10;
            draft.setSignupEnd(parse(value, dateOnly, "报名截止"));
            draft.setSignupEndPrecision(value == null ? "UNKNOWN" : dateOnly ? "DATE" : "DATETIME");
        }
        if (req.getStartTime() != null) {
            draft.setStartTime(parse(req.getStartTime(), false, "开赛时间"));
        }
        if (req.getSignupEndPrecision() != null && req.getSignupEnd() != null) {
            String precision = req.getSignupEndPrecision();
            if (!"DATE".equals(precision) && !"DATETIME".equals(precision) && !"UNKNOWN".equals(precision)) {
                throw new BusinessException(400, "报名截止精度取值不正确");
            }
            draft.setSignupEndPrecision(precision);
        }
    }

    private void recompute(CompetitionDraft draft) {
        List<String> missing = new ArrayList<>();
        if (draft.getName() == null) missing.add("赛事名称");
        if (draft.getOrganizer() == null) missing.add("主办方");
        if (draft.getSignupEnd() == null) missing.add("报名截止时间");
        if (draft.getEligibilityText() == null) missing.add("参赛条件");
        draft.setMissingFields(json(missing));

        List<String> conflicts = readList(draft.getConflicts());
        if (draft.getSignupStart() != null && draft.getSignupEnd() != null
                && draft.getSignupStart().isAfter(draft.getSignupEnd())
                && conflicts.stream().noneMatch(c -> c.contains("报名开始时间晚于"))) {
            conflicts.add("报名开始时间晚于报名截止时间，需人工核对");
        }
        if (draft.getStartTime() != null && draft.getSignupEnd() != null
                && draft.getStartTime().isBefore(draft.getSignupEnd())
                && conflicts.stream().noneMatch(c -> c.contains("开赛时间早于"))) {
            conflicts.add("开赛时间早于报名截止时间，可能是多阶段赛事或日期归属不同，需人工核对");
        }
        draft.setConflicts(json(conflicts));

        if (draft.getSignupEnd() != null && !draft.getSignupEnd().isAfter(LocalDateTime.now(clock))) {
            draft.setStaleReason("报名已于 " + draft.getSignupEnd().toLocalDate() + " 截止，不能作为近期赛事发布");
        } else {
            draft.setStaleReason(null);
        }
    }

    List<String> blockers(CompetitionDraft draft) {
        List<String> blockers = new ArrayList<>();
        if (isBlank(draft.getName())) blockers.add("缺少赛事名称");
        if (isBlank(draft.getOrganizer())) blockers.add("缺少主办方");
        if (draft.getSignupEnd() == null) blockers.add("缺少报名截止时间");
        if (draft.getSignupStart() != null && draft.getSignupEnd() != null
                && draft.getSignupStart().isAfter(draft.getSignupEnd())) {
            blockers.add("报名开始时间晚于截止时间");
        }
        if (draft.getSignupEnd() != null && !draft.getSignupEnd().isAfter(LocalDateTime.now(clock))) {
            blockers.add("草稿报名已截止，不能作为近期赛事发布");
        }
        List<CompetitionSource> sources = draft.getId() == null ? List.of() : sources(draft.getId());
        boolean urlSource = draft.getNormalizedUrl() != null && !draft.getNormalizedUrl().isBlank();
        boolean manualSource = sources.stream().anyMatch(s -> Boolean.TRUE.equals(s.getManual())
                && s.getRawText() != null && !s.getRawText().isBlank());
        if (!urlSource && !manualSource) {
            blockers.add("缺少可追溯来源（URL 或人工粘贴的公告正文）");
        }
        if (!urlSource && manualSource && isBlank(draft.getReviewNote())) {
            blockers.add("人工粘贴文本需在审核备注中注明来源");
        }
        if (sources.isEmpty() && draft.getId() != null) {
            blockers.add("缺少来源记录");
        }
        if ("DATE".equals(draft.getSignupEndPrecision())
                && !Boolean.TRUE.equals(draft.getDateInterpretationConfirmed())) {
            blockers.add("报名截止只有日期，需确认按当日 23:59:59 解释");
        }
        List<DuplicateCandidateVO> duplicates = readDuplicates(draft);
        boolean sameInstance = duplicates.stream().anyMatch(this::isSameInstance);
        if (sameInstance) {
            blockers.add("草稿与现有赛事/草稿为同一实例，确认差异也不能放行；请合并，或补全年份/届次/赛道");
        } else if (!duplicates.isEmpty() && !Boolean.TRUE.equals(draft.getDuplicateAcknowledged())) {
            blockers.add("存在疑似重复赛事，请核对并确认差异");
        } else if (!duplicates.isEmpty() && isBlank(draft.getDuplicateNote())) {
            blockers.add("请填写差异说明（如新届次/新赛道的具体年份与范围）");
        }
        if (!readList(draft.getConflicts()).isEmpty() && isBlank(draft.getReviewNote())) {
            blockers.add("存在日期/来源冲突，请在审核备注中说明处理结果");
        }
        return blockers;
    }

    private boolean isSameInstance(DuplicateCandidateVO candidate) {
        return candidate.getSimilarity() >= 100
                && candidate.getReason() != null
                && candidate.getReason().startsWith("同一实例");
    }

    // ==================== VO ====================

    private CompetitionDraftVO toVO(CompetitionDraft draft) {
        CompetitionDraftVO vo = new CompetitionDraftVO();
        vo.setId(draft.getId());
        vo.setName(draft.getName());
        vo.setOrganizer(draft.getOrganizer());
        vo.setYear(draft.getCompetitionYear());
        vo.setEdition(draft.getEdition());
        vo.setTrack(draft.getTrack());
        vo.setLevel(draft.getLevel());
        vo.setDescription(draft.getDescription());
        vo.setTags(draft.getTags());
        vo.setEligibilityText(draft.getEligibilityText());
        vo.setSignupStart(draft.getSignupStart());
        vo.setSignupEnd(draft.getSignupEnd());
        vo.setStartTime(draft.getStartTime());
        vo.setSignupEndPrecision(draft.getSignupEndPrecision());
        vo.setDateNote(draft.getDateNote());
        vo.setDateInterpretationConfirmed(draft.getDateInterpretationConfirmed());
        vo.setSourceUrl(draft.getSourceUrl());
        vo.setNormalizedUrl(draft.getNormalizedUrl());
        vo.setPageTitle(draft.getPageTitle());
        vo.setSourceOrg(draft.getSourceOrg());
        vo.setPublishedAt(draft.getPublishedAt());
        vo.setFetchedAt(draft.getFetchedAt());
        vo.setSourceType(draft.getSourceType());
        vo.setExtractionMode(draft.getExtractionMode());
        vo.setStatus(draft.getStatus());
        vo.setVersion(draft.getVersion());
        vo.setReviewedVersion(draft.getReviewedVersion());
        vo.setMissingFields(readList(draft.getMissingFields()));
        vo.setConflicts(readList(draft.getConflicts()));
        vo.setWarnings(readList(draft.getWarnings()));
        vo.setDuplicateCandidates(readDuplicates(draft));
        vo.setDuplicateAcknowledged(draft.getDuplicateAcknowledged());
        vo.setDuplicateNote(draft.getDuplicateNote());
        vo.setStaleReason(draft.getStaleReason());
        vo.setReviewNote(draft.getReviewNote());
        vo.setPublishedCompetitionId(draft.getPublishedCompetitionId());
        vo.setCreatedAt(draft.getCreatedAt());
        vo.setUpdatedAt(draft.getUpdatedAt());
        vo.setSourceHost(host(draft.getSourceUrl()));

        List<CompetitionSource> sources = sources(draft.getId());
        vo.setSources(sources);
        Map<String, String> evidence = new LinkedHashMap<>();
        for (CompetitionSource source : sources) {
            evidence.putAll(readMap(source.getEvidenceJson()));
        }
        vo.setEvidence(evidence);
        vo.setReviews(reviewMapper.selectList(new LambdaQueryWrapper<CompetitionDraftReview>()
                .eq(CompetitionDraftReview::getDraftId, draft.getId())
                .orderByDesc(CompetitionDraftReview::getId)));
        vo.setPublishBlockers(blockers(draft));
        return vo;
    }

    private List<CompetitionSource> sources(Long draftId) {
        return sourceMapper.selectList(new LambdaQueryWrapper<CompetitionSource>()
                .eq(CompetitionSource::getDraftId, draftId)
                .orderByAsc(CompetitionSource::getId));
    }

    private void review(Long draftId, String action, String from, String to,
                        Long operatorId, String note, String changeSummary, Integer version) {
        CompetitionDraftReview review = new CompetitionDraftReview();
        review.setDraftId(draftId);
        review.setAction(action);
        review.setFromStatus(from);
        review.setToStatus(to);
        review.setOperatorId(operatorId);
        review.setNote(limit(note, 1000));
        review.setChangeSummary(limit(changeSummary, 1000));
        review.setVersion(version);
        review.setCreatedAt(LocalDateTime.now(clock));
        reviewMapper.insert(review);
    }

    // ==================== 工具 ====================

    private LocalDateTime parse(String value, boolean endOfDay, String label) {
        String text = blankToNull(value);
        if (text == null) return null;
        try {
            if (text.length() <= 10) {
                LocalDate date = LocalDate.parse(text);
                return endOfDay ? date.atTime(23, 59, 59) : date.atStartOfDay();
            }
            return LocalDateTime.parse(text, FMT);
        } catch (Exception e) {
            throw new BusinessException(400, label + "格式不正确，应为 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss");
        }
    }

    private String format(LocalDateTime value) {
        return value == null ? null : FMT.format(value);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<String> readList(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() { });
        } catch (Exception e) {
            List<String> values = new ArrayList<>();
            for (String part : json.split("[\\n;；]")) {
                if (!part.isBlank()) values.add(part.trim());
            }
            return values;
        }
    }

    private List<DuplicateCandidateVO> readDuplicates(CompetitionDraft draft) {
        if (draft.getDuplicateCandidates() == null || draft.getDuplicateCandidates().isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(draft.getDuplicateCandidates(),
                    new TypeReference<List<DuplicateCandidateVO>>() { });
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private Map<String, String> readMap(String json) {
        if (json == null || json.isBlank()) return new LinkedHashMap<>();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() { });
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private String host(String url) {
        if (url == null) return null;
        try {
            return URI.create(url).getHost();
        } catch (Exception e) {
            return null;
        }
    }

    private String normalizeText(String value) {
        return value == null || value.isBlank()
                ? null : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String limit(String value, int max) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
