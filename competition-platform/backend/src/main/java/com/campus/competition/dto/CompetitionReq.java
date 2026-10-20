package com.campus.competition.dto;

import javax.validation.constraints.NotBlank;

/**
 * 竞赛信息请求（管理端）
 */
public class CompetitionReq {

    @NotBlank(message = "竞赛名称不能为空")
    private String name;

    private String organizer;
    private String level;
    private String signupStart;
    private String signupEnd;
    private String startTime;
    private String description;
    private String tags;
    private String eligibilityText;
    private Integer year;
    private String edition;
    private String track;
    private String sourceUrl;
    private String sourceType;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getOrganizer() {
        return organizer;
    }

    public void setOrganizer(String organizer) {
        this.organizer = organizer;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getSignupStart() {
        return signupStart;
    }

    public void setSignupStart(String signupStart) {
        this.signupStart = signupStart;
    }

    public String getSignupEnd() {
        return signupEnd;
    }

    public void setSignupEnd(String signupEnd) {
        this.signupEnd = signupEnd;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getEligibilityText() {
        return eligibilityText;
    }

    public void setEligibilityText(String eligibilityText) {
        this.eligibilityText = eligibilityText;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public String getEdition() {
        return edition;
    }

    public void setEdition(String edition) {
        this.edition = edition;
    }

    public String getTrack() {
        return track;
    }

    public void setTrack(String track) {
        this.track = track;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }
}
