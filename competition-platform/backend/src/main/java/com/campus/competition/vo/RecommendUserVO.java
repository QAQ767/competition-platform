package com.campus.competition.vo;

import java.util.List;

/**
 * AI 推荐队友视图
 */
public class RecommendUserVO {

    private Long userId;
    private String nickname;
    private String college;
    private String major;
    private List<String> skills;
    private String competeStatus;
    private Integer matchScore;
    private String reason;
    /** 推荐理由来源：LLM / RULE */
    private String reasonSource;
    private Boolean hasExperience;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getCollege() {
        return college;
    }

    public void setCollege(String college) {
        this.college = college;
    }

    public String getMajor() {
        return major;
    }

    public void setMajor(String major) {
        this.major = major;
    }

    public List<String> getSkills() {
        return skills;
    }

    public void setSkills(List<String> skills) {
        this.skills = skills;
    }

    public String getCompeteStatus() {
        return competeStatus;
    }

    public void setCompeteStatus(String competeStatus) {
        this.competeStatus = competeStatus;
    }

    public Integer getMatchScore() {
        return matchScore;
    }

    public void setMatchScore(Integer matchScore) {
        this.matchScore = matchScore;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getReasonSource() {
        return reasonSource;
    }

    public void setReasonSource(String reasonSource) {
        this.reasonSource = reasonSource;
    }

    public Boolean getHasExperience() {
        return hasExperience;
    }

    public void setHasExperience(Boolean hasExperience) {
        this.hasExperience = hasExperience;
    }
}
