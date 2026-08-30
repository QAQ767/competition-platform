package com.campus.competition.service;

import com.campus.competition.entity.Competition;
import com.campus.competition.entity.Team;
import com.campus.competition.mapper.AchievementMapper;
import com.campus.competition.mapper.CompetitionMapper;
import com.campus.competition.mapper.TeamMapper;
import com.campus.competition.mapper.UserMapper;
import com.campus.competition.vo.StatsVO;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 数据统计服务（v2.1：Redis 缓存概览，TTL 抖动防雪崩）
 */
@Service
public class StatsService {

    private static final String CACHE_KEY = "cache:stats:overview";
    private static final long CACHE_TTL_SECONDS = 300;

    private final UserMapper userMapper;
    private final TeamMapper teamMapper;
    private final CompetitionMapper competitionMapper;
    private final AchievementMapper achievementMapper;
    private final CacheService cacheService;

    public StatsService(UserMapper userMapper, TeamMapper teamMapper,
                        CompetitionMapper competitionMapper, AchievementMapper achievementMapper,
                        CacheService cacheService) {
        this.userMapper = userMapper;
        this.teamMapper = teamMapper;
        this.competitionMapper = competitionMapper;
        this.achievementMapper = achievementMapper;
        this.cacheService = cacheService;
    }

    public StatsVO overview() {
        StatsVO cached = cacheService.get(CACHE_KEY, StatsVO.class);
        if (cached != null) {
            return cached;
        }
        StatsVO vo = new StatsVO();
        vo.setUserCount(userMapper.selectCount(null));
        vo.setTeamCount(teamMapper.selectCount(null));
        vo.setCompetitionCount(competitionMapper.selectCount(null));
        vo.setAchievementCount(achievementMapper.selectCount(null));
        vo.setHotCompetitions(hotCompetitions());
        cacheService.putJittered(CACHE_KEY, vo, CACHE_TTL_SECONDS, 60);
        return vo;
    }

    private List<StatsVO.HotCompetitionVO> hotCompetitions() {
        List<Team> teams = teamMapper.selectList(null);
        Map<Long, Long> countByCompetition = teams.stream()
                .filter(t -> t.getCompetitionId() != null)
                .collect(Collectors.groupingBy(Team::getCompetitionId, LinkedHashMap::new, Collectors.counting()));

        return countByCompetition.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(5)
                .map(entry -> {
                    Competition competition = competitionMapper.selectById(entry.getKey());
                    String name = competition == null ? "未知竞赛" : competition.getName();
                    return new StatsVO.HotCompetitionVO(name, entry.getValue());
                })
                .collect(Collectors.toList());
    }
}
