package com.campus.competition.dto;

import javax.validation.constraints.NotBlank;

/**
 * 成果发布请求（需携带证明文件 URL，经管理员审核后公开）
 */
public class AchievementReq {

    private Long competitionId;

    /** 获奖队伍 id；不传（null）表示个人奖项，不关联队伍 */
    private Long teamId;

    @NotBlank(message = "成果名称不能为空")
    private String name;

    /** 校级 / 省级 / 国家级 */
    private String level;

    @NotBlank(message = "获奖情况不能为空")
    private String award;

    private Integer awardYear;

    /** 证明文件 URL（发布必填，先上传文件获得） */
    @NotBlank(message = "请上传成果证明文件")
    private String proof;

    public Long getCompetitionId() {
        return competitionId;
    }

    public void setCompetitionId(Long competitionId) {
        this.competitionId = competitionId;
    }

    public Long getTeamId() {
        return teamId;
    }

    public void setTeamId(Long teamId) {
        this.teamId = teamId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getAward() {
        return award;
    }

    public void setAward(String award) {
        this.award = award;
    }

    public Integer getAwardYear() {
        return awardYear;
    }

    public void setAwardYear(Integer awardYear) {
        this.awardYear = awardYear;
    }

    public String getProof() {
        return proof;
    }

    public void setProof(String proof) {
        this.proof = proof;
    }
}
