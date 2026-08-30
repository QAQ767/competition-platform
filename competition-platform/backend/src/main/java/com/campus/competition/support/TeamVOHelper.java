package com.campus.competition.support;

import com.campus.competition.entity.Competition;
import com.campus.competition.entity.Team;
import com.campus.competition.entity.User;
import com.campus.competition.mapper.CompetitionMapper;
import com.campus.competition.mapper.UserMapper;
import com.campus.competition.vo.TeamVO;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 队伍视图转换助手
 */
public final class TeamVOHelper {

    private TeamVOHelper() {
    }

    public static TeamVO toTeamVO(Team team, CompetitionMapper competitionMapper, UserMapper userMapper) {
        if (team == null) {
            return null;
        }
        TeamVO vo = new TeamVO();
        vo.setId(team.getId());
        vo.setCompetitionId(team.getCompetitionId());
        vo.setCaptainId(team.getCaptainId());
        vo.setTitle(team.getTitle());
        vo.setDescription(team.getDescription());
        vo.setMemberCount(team.getMemberCount());
        vo.setMaxMembers(team.getMaxMembers());
        vo.setStatus(team.getStatus());
        vo.setDeadline(team.getDeadline());
        vo.setCreatedAt(team.getCreatedAt());
        vo.setSkills(splitSkills(team.getSkills()));

        Competition competition = competitionMapper.selectById(team.getCompetitionId());
        vo.setCompetitionName(competition == null ? "未知竞赛" : competition.getName());

        User captain = userMapper.selectById(team.getCaptainId());
        vo.setCaptainName(captain == null ? "未知" : (captain.getNickname() == null ? captain.getUsername() : captain.getNickname()));
        return vo;
    }

    public static List<String> splitSkills(String skills) {
        if (skills == null || skills.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(skills.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}
