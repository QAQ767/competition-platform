package com.campus.competition.vo;

import com.campus.competition.entity.CompetitionDraftReview;
import com.campus.competition.entity.CompetitionSource;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 管理员草稿详情：字段、来源、逐字段证据、审核轨迹和发布阻塞项。 */
public class CompetitionDraftVO {

    private Long id;
    private String name;
    private String organizer;
    private Integer year;
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
    private String signupEndPrecision;
    private String dateNote;
    private Boolean dateInterpretationConfirmed;
    private String sourceUrl;
    private String normalizedUrl;
    private String pageTitle;
    private String sourceOrg;
    private String sourceHost;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime publishedAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime fetchedAt;
    private String sourceType;
    private String extractionMode;
    private String status;
    private Integer version;
    /** 审核生效的版本号；与 version 不一致说明审核后内容被修改。 */
    private Integer reviewedVersion;
    private List<String> missingFields = new ArrayList<>();
    private List<String> conflicts = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
    private List<DuplicateCandidateVO> duplicateCandidates = new ArrayList<>();
    private Boolean duplicateAcknowledged;
    private String duplicateNote;
    private String staleReason;
    private String reviewNote;
    private Long publishedCompetitionId;
    private List<String> publishBlockers = new ArrayList<>();
    private List<CompetitionSource> sources = new ArrayList<>();
    private Map<String, String> evidence = new LinkedHashMap<>();
    private List<CompetitionDraftReview> reviews = new ArrayList<>();
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
    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }
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
    public String getSourceHost() { return sourceHost; }
    public void setSourceHost(String sourceHost) { this.sourceHost = sourceHost; }
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
    public List<String> getMissingFields() { return missingFields; }
    public void setMissingFields(List<String> missingFields) { this.missingFields = missingFields; }
    public List<String> getConflicts() { return conflicts; }
    public void setConflicts(List<String> conflicts) { this.conflicts = conflicts; }
    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> warnings) { this.warnings = warnings; }
    public List<DuplicateCandidateVO> getDuplicateCandidates() { return duplicateCandidates; }
    public void setDuplicateCandidates(List<DuplicateCandidateVO> duplicateCandidates) { this.duplicateCandidates = duplicateCandidates; }
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
    public List<String> getPublishBlockers() { return publishBlockers; }
    public void setPublishBlockers(List<String> publishBlockers) { this.publishBlockers = publishBlockers; }
    public List<CompetitionSource> getSources() { return sources; }
    public void setSources(List<CompetitionSource> sources) { this.sources = sources; }
    public Map<String, String> getEvidence() { return evidence; }
    public void setEvidence(Map<String, String> evidence) { this.evidence = evidence; }
    public List<CompetitionDraftReview> getReviews() { return reviews; }
    public void setReviews(List<CompetitionDraftReview> reviews) { this.reviews = reviews; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
