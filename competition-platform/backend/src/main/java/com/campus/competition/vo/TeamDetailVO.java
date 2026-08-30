package com.campus.competition.vo;

import com.campus.competition.entity.TeamApplication;
import com.campus.competition.entity.TeamMember;

import java.util.List;

/**
 * 团队详情视图
 */
public class TeamDetailVO {

    private TeamVO team;
    private List<TeamMember> members;
    private List<TeamApplication> applications;

    public TeamVO getTeam() {
        return team;
    }

    public void setTeam(TeamVO team) {
        this.team = team;
    }

    public List<TeamMember> getMembers() {
        return members;
    }

    public void setMembers(List<TeamMember> members) {
        this.members = members;
    }

    public List<TeamApplication> getApplications() {
        return applications;
    }

    public void setApplications(List<TeamApplication> applications) {
        this.applications = applications;
    }
}
