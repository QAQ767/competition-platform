package com.campus.competition.vo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 学生参赛推荐结果。mode=RULE 时 fallbackReason 必须说明原因。 */
public class StudentRecommendationVO {

    private int retryCount;
    private String failureCategory;
    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int v) { retryCount = v; }
    public String getFailureCategory() { return failureCategory; }
    public void setFailureCategory(String v) { failureCategory = v; }
    private String mode;
    private String fallbackReason;
    private LocalDateTime generatedAt;
    private AdviceProfileSummaryVO profileSummary;
    private List<String> missingFields = new ArrayList<>();
    private String confidence;
    private String insight;
    private List<String> notes = new ArrayList<>();
    private Integer windowDays;
    private List<CompetitionRecommendationVO> competitions = new ArrayList<>();
    private List<TeamRecommendationVO> teams = new ArrayList<>();
    private List<AppliedTeamVO> appliedTeams = new ArrayList<>();

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getFallbackReason() { return fallbackReason; }
    public void setFallbackReason(String fallbackReason) { this.fallbackReason = fallbackReason; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }
    public AdviceProfileSummaryVO getProfileSummary() { return profileSummary; }
    public void setProfileSummary(AdviceProfileSummaryVO profileSummary) { this.profileSummary = profileSummary; }
    public List<String> getMissingFields() { return missingFields; }
    public void setMissingFields(List<String> missingFields) { this.missingFields = missingFields; }
    public String getConfidence() { return confidence; }
    public void setConfidence(String confidence) { this.confidence = confidence; }
    public String getInsight() { return insight; }
    public void setInsight(String insight) { this.insight = insight; }
    public List<String> getNotes() { return notes; }
    public void setNotes(List<String> notes) { this.notes = notes; }
    public Integer getWindowDays() { return windowDays; }
    public void setWindowDays(Integer windowDays) { this.windowDays = windowDays; }
    public List<CompetitionRecommendationVO> getCompetitions() { return competitions; }
    public void setCompetitions(List<CompetitionRecommendationVO> competitions) { this.competitions = competitions; }
    public List<TeamRecommendationVO> getTeams() { return teams; }
    public void setTeams(List<TeamRecommendationVO> teams) { this.teams = teams; }
    public List<AppliedTeamVO> getAppliedTeams() { return appliedTeams; }
    public void setAppliedTeams(List<AppliedTeamVO> appliedTeams) { this.appliedTeams = appliedTeams; }
}
