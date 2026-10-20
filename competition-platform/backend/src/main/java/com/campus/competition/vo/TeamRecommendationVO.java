package com.campus.competition.vo;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 队伍推荐卡片。成员技能只给匿名摘要，不包含邮箱、账号或完整档案。 */
public class TeamRecommendationVO {

    private Long id;
    private Long competitionId;
    private String competitionName;
    private String title;
    private String captainName;
    private Integer memberCount;
    private Integer maxMembers;
    private Integer remainingPlaces;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime deadline;
    private List<String> requiredSkills = new ArrayList<>();
    private List<String> memberSkillSummary = new ArrayList<>();
    private String suggestedRole;
    private String category;
    private String categoryLabel;
    private Integer matchScore;
    private Double evidenceCompleteness;
    private String reason;
    private List<String> risks = new ArrayList<>();
    private String nextStep;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCompetitionId() { return competitionId; }
    public void setCompetitionId(Long competitionId) { this.competitionId = competitionId; }
    public String getCompetitionName() { return competitionName; }
    public void setCompetitionName(String competitionName) { this.competitionName = competitionName; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getCaptainName() { return captainName; }
    public void setCaptainName(String captainName) { this.captainName = captainName; }
    public Integer getMemberCount() { return memberCount; }
    public void setMemberCount(Integer memberCount) { this.memberCount = memberCount; }
    public Integer getMaxMembers() { return maxMembers; }
    public void setMaxMembers(Integer maxMembers) { this.maxMembers = maxMembers; }
    public Integer getRemainingPlaces() { return remainingPlaces; }
    public void setRemainingPlaces(Integer remainingPlaces) { this.remainingPlaces = remainingPlaces; }
    public LocalDateTime getDeadline() { return deadline; }
    public void setDeadline(LocalDateTime deadline) { this.deadline = deadline; }
    public List<String> getRequiredSkills() { return requiredSkills; }
    public void setRequiredSkills(List<String> requiredSkills) { this.requiredSkills = requiredSkills; }
    public List<String> getMemberSkillSummary() { return memberSkillSummary; }
    public void setMemberSkillSummary(List<String> memberSkillSummary) { this.memberSkillSummary = memberSkillSummary; }
    public String getSuggestedRole() { return suggestedRole; }
    public void setSuggestedRole(String suggestedRole) { this.suggestedRole = suggestedRole; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getCategoryLabel() { return categoryLabel; }
    public void setCategoryLabel(String categoryLabel) { this.categoryLabel = categoryLabel; }
    public Integer getMatchScore() { return matchScore; }
    public void setMatchScore(Integer matchScore) { this.matchScore = matchScore; }
    public Double getEvidenceCompleteness() { return evidenceCompleteness; }
    public void setEvidenceCompleteness(Double evidenceCompleteness) { this.evidenceCompleteness = evidenceCompleteness; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public List<String> getRisks() { return risks; }
    public void setRisks(List<String> risks) { this.risks = risks; }
    public String getNextStep() { return nextStep; }
    public void setNextStep(String nextStep) { this.nextStep = nextStep; }
}
