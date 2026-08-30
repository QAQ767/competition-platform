package com.campus.competition.vo;

import java.util.List;

/**
 * 平台数据统计视图
 */
public class StatsVO {

    private Long userCount;
    private Long teamCount;
    private Long competitionCount;
    private Long achievementCount;
    private List<HotCompetitionVO> hotCompetitions;

    public static class HotCompetitionVO {
        private String name;
        private Long teamCount;

        public HotCompetitionVO() {
        }

        public HotCompetitionVO(String name, Long teamCount) {
            this.name = name;
            this.teamCount = teamCount;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Long getTeamCount() {
            return teamCount;
        }

        public void setTeamCount(Long teamCount) {
            this.teamCount = teamCount;
        }
    }

    public Long getUserCount() {
        return userCount;
    }

    public void setUserCount(Long userCount) {
        this.userCount = userCount;
    }

    public Long getTeamCount() {
        return teamCount;
    }

    public void setTeamCount(Long teamCount) {
        this.teamCount = teamCount;
    }

    public Long getCompetitionCount() {
        return competitionCount;
    }

    public void setCompetitionCount(Long competitionCount) {
        this.competitionCount = competitionCount;
    }

    public Long getAchievementCount() {
        return achievementCount;
    }

    public void setAchievementCount(Long achievementCount) {
        this.achievementCount = achievementCount;
    }

    public List<HotCompetitionVO> getHotCompetitions() {
        return hotCompetitions;
    }

    public void setHotCompetitions(List<HotCompetitionVO> hotCompetitions) {
        this.hotCompetitions = hotCompetitions;
    }
}
