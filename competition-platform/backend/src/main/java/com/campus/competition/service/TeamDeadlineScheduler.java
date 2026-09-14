package com.campus.competition.service;

import com.campus.competition.mapper.TeamMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 无需用户访问页面，每 30 秒在数据库中结束已到组队截止时间的队伍。 */
@Component
public class TeamDeadlineScheduler {
    private static final Logger log = LoggerFactory.getLogger(TeamDeadlineScheduler.class);
    private final TeamMapper teamMapper;

    public TeamDeadlineScheduler(TeamMapper teamMapper) {
        this.teamMapper = teamMapper;
    }

    @Scheduled(fixedRate = 30_000, initialDelay = 30_000)
    public void checkDeadlines() {
        int closed = teamMapper.closeExpiredTeams(LocalDateTime.now());
        if (closed > 0) log.info("组队截止检查：已将 {} 支到期队伍更新为已结束", closed);
    }
}
