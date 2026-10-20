package com.campus.competition.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.competition.common.BusinessException;
import com.campus.competition.dto.CompetitionReq;
import com.campus.competition.entity.Competition;
import com.campus.competition.mapper.CompetitionMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 竞赛服务（v2.1：Redis 缓存，写操作自动失效）
 */
@Service
public class CompetitionService {
    private static final Logger log = LoggerFactory.getLogger(CompetitionService.class);

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FMT_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    /** 无筛选条件的竞赛列表缓存 */
    private static final String CACHE_KEY = "cache:competitions";
    private static final long CACHE_TTL_SECONDS = 60;

    private final CompetitionMapper competitionMapper;
    private final CacheService cacheService;

    public CompetitionService(CompetitionMapper competitionMapper, CacheService cacheService) {
        this.competitionMapper = competitionMapper;
        this.cacheService = cacheService;
    }

    public synchronized List<Competition> list(String keyword, String level, String status) {
        // 无筛选条件时走缓存（热点数据），带筛选直接查库
        if ((keyword == null || keyword.isBlank())
                && (level == null || level.isBlank())
                && (status == null || status.isBlank())) {
            List<Competition> cached = cacheService.get(CACHE_KEY, new TypeReference<List<Competition>>() {
            });
            if (cached != null) {
                return cached;
            }
            List<Competition> list = queryAll();
            cacheService.putJittered(CACHE_KEY, list, CACHE_TTL_SECONDS, 15);
            return list;
        }
        LambdaQueryWrapper<Competition> qw = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            qw.and(w -> w.like(Competition::getName, keyword)
                    .or().like(Competition::getDescription, keyword));
        }
        if (level != null && !level.isBlank()) {
            qw.eq(Competition::getLevel, level);
        }
        if ("即将开赛".equals(status)) {
            LocalDateTime now = LocalDateTime.now();
            qw.gt(Competition::getStartTime, now).lt(Competition::getStartTime, now.plusDays(7));
        } else if (status != null && !status.isBlank()) {
            qw.eq(Competition::getStatus, status);
        }
        qw.orderByAsc(Competition::getSignupEnd);
        return competitionMapper.selectList(qw);
    }

    private List<Competition> queryAll() {
        return competitionMapper.selectList(
                new LambdaQueryWrapper<Competition>().orderByAsc(Competition::getSignupEnd));
    }

    public synchronized Competition create(CompetitionReq req) {
        Competition competition = new Competition();
        competition.setName(req.getName());
        competition.setOrganizer(req.getOrganizer());
        competition.setLevel(req.getLevel());
        competition.setSignupStart(parse(req.getSignupStart()));
        competition.setSignupEnd(parseEnd(req.getSignupEnd()));
        competition.setStartTime(parse(req.getStartTime()));
        competition.setDescription(req.getDescription());
        competition.setTags(blankToNull(req.getTags()));
        competition.setEligibilityText(blankToNull(req.getEligibilityText()));
        competition.setCompetitionYear(req.getYear());
        competition.setEdition(blankToNull(req.getEdition()));
        competition.setTrack(blankToNull(req.getTrack()));
        competition.setSourceUrl(blankToNull(req.getSourceUrl()));
        competition.setSourceType(req.getSourceType() == null || req.getSourceType().isBlank()
                ? "PLATFORM" : req.getSourceType());
        competition.setStatus(computeStatus(competition.getSignupStart(), competition.getSignupEnd()));
        applyIdentity(competition);
        try {
            competitionMapper.insert(competition);
        } catch (org.springframework.dao.DuplicateKeyException duplicate) {
            throw new BusinessException(409,
                    "已存在同一赛事（名称、主办方、年份、届次、赛道相同），请核对后修订原记录");
        }
        cacheService.delete(CACHE_KEY);
        return competition;
    }

    public Competition detail(Long id) {
        Competition competition = competitionMapper.selectById(id);
        if (competition == null) {
            throw new BusinessException("竞赛不存在");
        }
        return competition;
    }

    public synchronized void delete(Long id) {
        if (competitionMapper.selectById(id) == null) {
            throw new BusinessException("竞赛不存在");
        }
        competitionMapper.deleteById(id);
        cacheService.delete(CACHE_KEY);
    }

    /** 管理员明确更正既有赛事；AI 不会自动改写已发布赛事。 */
    public synchronized Competition update(Long id, CompetitionReq req) {
        Competition competition = competitionMapper.selectById(id);
        if (competition == null) {
            throw new BusinessException("竞赛不存在");
        }
        if (req.getName() == null || req.getName().isBlank()) {
            throw new BusinessException(400, "竞赛名称不能为空");
        }
        competition.setName(req.getName().trim());
        competition.setOrganizer(req.getOrganizer());
        competition.setLevel(req.getLevel());
        competition.setSignupStart(parse(req.getSignupStart()));
        competition.setSignupEnd(parseEnd(req.getSignupEnd()));
        competition.setStartTime(parse(req.getStartTime()));
        competition.setDescription(req.getDescription());
        competition.setTags(blankToNull(req.getTags()));
        competition.setEligibilityText(blankToNull(req.getEligibilityText()));
        competition.setCompetitionYear(req.getYear());
        competition.setEdition(blankToNull(req.getEdition()));
        competition.setTrack(blankToNull(req.getTrack()));
        competition.setSourceUrl(blankToNull(req.getSourceUrl()));
        competition.setSourceType(req.getSourceType() == null || req.getSourceType().isBlank()
                ? competition.getSourceType() : req.getSourceType());
        competition.setStatus(computeStatus(competition.getSignupStart(), competition.getSignupEnd()));
        applyIdentity(competition);
        try {
            competitionMapper.updateById(competition);
        } catch (org.springframework.dao.DuplicateKeyException duplicate) {
            throw new BusinessException(409,
                    "修改后与另一条赛事记录身份相同（名称、主办方、年份、届次、赛道），请先核对是否重复");
        }
        cacheService.delete(CACHE_KEY);
        return competition;
    }

    /** 写入赛事实例身份键；身份不完整时留空，不建立可能误伤新一届的唯一键。 */
    private void applyIdentity(Competition competition) {
        competition.setIdentityKey(com.campus.competition.support.CompetitionIdentity.key(
                competition.getName(), competition.getOrganizer(), competition.getCompetitionYear(),
                competition.getEdition(), competition.getTrack()));
    }

    // 与列表缓存读写使用同一把锁，避免扫描清缓存后旧查询再次写回旧状态。
    @Scheduled(fixedRate = 30_000, initialDelay = 30_000)
    public synchronized void refreshStatuses() {
        LocalDateTime now = LocalDateTime.now();
        int changed = competitionMapper.refreshStatuses(now, now.plusDays(7));
        // 每轮均清理，上一轮 Redis 暂时不可用时下一轮仍能重试失效。
        cacheService.delete(CACHE_KEY);
        if (changed > 0) log.info("竞赛时间检查：已更新 {} 项竞赛的报名状态", changed);
    }

    private LocalDateTime parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(text, FMT);
        } catch (Exception e) {
            return java.time.LocalDate.parse(text, FMT_DATE).atStartOfDay();
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** 只有日期的截止按北京时间当日 23:59:59，避免被当成 00:00 提前截止。 */
    private LocalDateTime parseEnd(String text) {
        LocalDateTime value = parse(text);
        if (value != null && text != null && text.trim().length() <= 10) {
            return value.toLocalDate().atTime(23, 59, 59);
        }
        return value;
    }

    private String computeStatus(LocalDateTime signupStart, LocalDateTime signupEnd) {
        LocalDateTime now = LocalDateTime.now();
        if (signupEnd != null && !now.isBefore(signupEnd)) {
            return "已结束";
        }
        if (signupStart != null && now.isBefore(signupStart)) {
            return "即将报名";
        }
        if (signupEnd != null && signupEnd.isBefore(now.plusDays(7))) {
            return "报名即将截止";
        }
        return "报名中";
    }
}
