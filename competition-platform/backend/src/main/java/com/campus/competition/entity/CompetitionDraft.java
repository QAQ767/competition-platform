package com.campus.competition.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/**
 * 管理员赛事发现草稿。学生永远看不到本表；只有发布成功后才写入 competition。
 * 状态：DRAFT -> READY -> PUBLISHED，另有 REJECTED。
 */
@TableName("competition_draft")
public class CompetitionDraft {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String organizer;
    private Integer competitionYear;
    private String edition;
    private String track;
    private String level;
    private String description;
    private String tags;
    private String eligibilityText;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime signupStart;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime signupEnd;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;
    /** DATETIME / DATE / UNKNOWN：DATE 表示原文只有日期，已按当日 23:59:59 暂存待确认 */
    private String signupEndPrecision;
    private String dateNote;
    private Boolean dateInterpretationConfirmed;
    private String sourceUrl;
    private String normalizedUrl;
    private String pageTitle;
    private String sourceOrg;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime publishedAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime fetchedAt;
    /** OFFICIAL / AGGREGATOR / UNKNOWN / MANUAL_TEXT */
    private String sourceType;
    /** LLM / RULE / MANUAL */
    private String extractionMode;
    private String status;
    private Integer version;
    /** 通过 MARK_READY 时的版本号；发布时必须与当前版本一致，否则需重新审核。 */
    @com.baomidou.mybatisplus.annotation.TableField(
            updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private Integer reviewedVersion;
    private String missingFields;
    private String conflicts;
    private String warnings;
    private String duplicateCandidates;
    private Boolean duplicateAcknowledged;
    private String duplicateNote;
    private String staleReason;
    private String reviewNote;
    private Long publishedCompetitionId;
    private String publishKey;
    private Long createdBy;
    private Long updatedBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getOrganizer() { return organizer; }
    public void setOrganizer(String organizer) { this.organizer = organizer; }
    public Integer getCompetitionYear() { return competitionYear; }
    public void setCompetitionYear(Integer competitionYear) { this.competitionYear = competitionYear; }
    public String getEdition() { return edition; }
    public void setEdition(String edition) { this.edition = edition; }
    public String getTrack() { return track; }
    public void setTrack(String track) { this.track = track; }
    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
    public String getEligibilityText() { return eligibilityText; }
    public void setEligibilityText(String eligibilityText) { this.eligibilityText = eligibilityText; }
    public LocalDateTime getSignupStart() { return signupStart; }
    public void setSignupStart(LocalDateTime signupStart) { this.signupStart = signupStart; }
    public LocalDateTime getSignupEnd() { return signupEnd; }
    public void setSignupEnd(LocalDateTime signupEnd) { this.signupEnd = signupEnd; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public String getSignupEndPrecision() { return signupEndPrecision; }
    public void setSignupEndPrecision(String signupEndPrecision) { this.signupEndPrecision = signupEndPrecision; }
    public String getDateNote() { return dateNote; }
    public void setDateNote(String dateNote) { this.dateNote = dateNote; }
    public Boolean getDateInterpretationConfirmed() { return dateInterpretationConfirmed; }
    public void setDateInterpretationConfirmed(Boolean dateInterpretationConfirmed) { this.dateInterpretationConfirmed = dateInterpretationConfirmed; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public String getNormalizedUrl() { return normalizedUrl; }
    public void setNormalizedUrl(String normalizedUrl) { this.normalizedUrl = normalizedUrl; }
    public String getPageTitle() { return pageTitle; }
    public void setPageTitle(String pageTitle) { this.pageTitle = pageTitle; }
    public String getSourceOrg() { return sourceOrg; }
    public void setSourceOrg(String sourceOrg) { this.sourceOrg = sourceOrg; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
    public LocalDateTime getFetchedAt() { return fetchedAt; }
    public void setFetchedAt(LocalDateTime fetchedAt) { this.fetchedAt = fetchedAt; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getExtractionMode() { return extractionMode; }
    public void setExtractionMode(String extractionMode) { this.extractionMode = extractionMode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public Integer getReviewedVersion() { return reviewedVersion; }
    public void setReviewedVersion(Integer reviewedVersion) { this.reviewedVersion = reviewedVersion; }
    public String getMissingFields() { return missingFields; }
    public void setMissingFields(String missingFields) { this.missingFields = missingFields; }
    public String getConflicts() { return conflicts; }
    public void setConflicts(String conflicts) { this.conflicts = conflicts; }
    public String getWarnings() { return warnings; }
    public void setWarnings(String warnings) { this.warnings = warnings; }
    public String getDuplicateCandidates() { return duplicateCandidates; }
    public void setDuplicateCandidates(String duplicateCandidates) { this.duplicateCandidates = duplicateCandidates; }
    public Boolean getDuplicateAcknowledged() { return duplicateAcknowledged; }
    public void setDuplicateAcknowledged(Boolean duplicateAcknowledged) { this.duplicateAcknowledged = duplicateAcknowledged; }
    public String getDuplicateNote() { return duplicateNote; }
    public void setDuplicateNote(String duplicateNote) { this.duplicateNote = duplicateNote; }
    public String getStaleReason() { return staleReason; }
    public void setStaleReason(String staleReason) { this.staleReason = staleReason; }
    public String getReviewNote() { return reviewNote; }
    public void setReviewNote(String reviewNote) { this.reviewNote = reviewNote; }
    public Long getPublishedCompetitionId() { return publishedCompetitionId; }
    public void setPublishedCompetitionId(Long publishedCompetitionId) { this.publishedCompetitionId = publishedCompetitionId; }
    public String getPublishKey() { return publishKey; }
    public void setPublishKey(String publishKey) { this.publishKey = publishKey; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
