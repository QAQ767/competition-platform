package com.campus.competition.vo;

/** 已申请/已收到邀请的队伍，单独归类，避免重复推荐申请。 */
public class AppliedTeamVO {

    private Long teamId;
    private String title;
    private String status;
    private String competitionName;

    public AppliedTeamVO() { }

    public AppliedTeamVO(Long teamId, String title, String status, String competitionName) {
        this.teamId = teamId;
        this.title = title;
        this.status = status;
        this.competitionName = competitionName;
    }

    public Long getTeamId() { return teamId; }
    public void setTeamId(Long teamId) { this.teamId = teamId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCompetitionName() { return competitionName; }
    public void setCompetitionName(String competitionName) { this.competitionName = competitionName; }
}
