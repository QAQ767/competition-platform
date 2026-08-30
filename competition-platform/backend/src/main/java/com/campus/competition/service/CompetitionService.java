package com.campus.competition.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.competition.common.BusinessException;
import com.campus.competition.dto.CompetitionReq;
import com.campus.competition.entity.Competition;
import com.campus.competition.mapper.CompetitionMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 竞赛服务（v2.1：Redis 缓存，写操作自动失效）
 */
@Service
public class CompetitionService {

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

    public List<Competition> list(String keyword, String level, String status) {
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

    public Competition create(CompetitionReq req) {
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

    public void delete(Long id) {
        if (competitionMapper.selectById(id) == null) {
            throw new BusinessException("竞赛不存在");
        }
        competitionMapper.deleteById(id);
        cacheService.delete(CACHE_KEY);
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
        if (signupStart != null && now.isBefore(signupStart)) {
            return "即将开始";
        }
        if (signupEnd != null && now.isAfter(signupEnd)) {
            return "已结束";
        }
        return "报名中";
    }
}
