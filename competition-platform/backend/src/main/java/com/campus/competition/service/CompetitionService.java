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
        if (status != null && !status.isBlank()) {
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
        competition.setSignupEnd(parse(req.getSignupEnd()));
        competition.setStartTime(parse(req.getStartTime()));
        competition.setDescription(req.getDescription());
        competition.setStatus(computeStatus(competition.getSignupStart(), competition.getSignupEnd()));
        competitionMapper.insert(competition);
        cacheService.delete(CACHE_KEY);
        return competition;
    }

    public synchronized void delete(Long id) {
        if (competitionMapper.selectById(id) == null) {
            throw new BusinessException("竞赛不存在");
        }
        competitionMapper.deleteById(id);
        cacheService.delete(CACHE_KEY);
    }

    // 与列表缓存读写使用同一把锁，避免扫描清缓存后旧查询再次写回旧状态。
    @Scheduled(fixedRate = 30_000, initialDelay = 30_000)
    public synchronized void refreshStatuses() {
        int changed = competitionMapper.refreshStatuses(LocalDateTime.now());
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

    private String computeStatus(LocalDateTime signupStart, LocalDateTime signupEnd) {
        LocalDateTime now = LocalDateTime.now();
        if (signupEnd != null && !now.isBefore(signupEnd)) {
            return "已结束";
        }
        if (signupStart != null && now.isBefore(signupStart)) {
            return "即将开始";
        }
        return "报名中";
    }
}
