package com.campus.competition.vo;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 竞赛推荐卡片。所有字段均由数据库真实记录组装，模型只提供分数/理由/风险。 */
public class CompetitionRecommendationVO {

    private Long id;
    private String name;
    private String organizer;
    private String level;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime signupStart;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime signupEnd;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;
    private List<String> tags = new ArrayList<>();
    private String eligibilityText;
    /** OPEN / UPCOMING / NEEDS_CONFIRM */
    private String category;
    private String categoryLabel;
    private Integer matchScore;
    private Double evidenceCompleteness;
    private String reason;
    private List<String> risks = new ArrayList<>();
    private String nextStep;
    private String sourceType;
    private boolean weakDirection;
    private String status;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getOrganizer() { return organizer; }
    public void setOrganizer(String organizer) { this.organizer = organizer; }
    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
    public LocalDateTime getSignupStart() { return signupStart; }
    public void setSignupStart(LocalDateTime signupStart) { this.signupStart = signupStart; }
    public LocalDateTime getSignupEnd() { return signupEnd; }
    public void setSignupEnd(LocalDateTime signupEnd) { this.signupEnd = signupEnd; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
    public String getEligibilityText() { return eligibilityText; }
    public void setEligibilityText(String eligibilityText) { this.eligibilityText = eligibilityText; }
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
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public boolean isWeakDirection() { return weakDirection; }
    public void setWeakDirection(boolean weakDirection) { this.weakDirection = weakDirection; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
