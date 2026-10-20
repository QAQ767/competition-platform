package com.campus.competition.dto;

/** 管理员编辑/审核草稿。action: SAVE / MARK_READY / REJECT / REOPEN。 */
public class DraftUpdateReq {

    private Integer version;
    private String action;
    private String name;
    private String organizer;
    private Integer year;
    private String edition;
    private String track;
    private String level;
    private String description;
    private String tags;
    private String eligibilityText;
    private String signupStart;
    private String signupEnd;
    private String startTime;
    private String signupEndPrecision;
    private String dateNote;
    private Boolean confirmDateInterpretation;
    private Boolean duplicateAcknowledged;
    private String duplicateNote;
    private String reviewNote;

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
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
    public String getSignupStart() { return signupStart; }
    public void setSignupStart(String signupStart) { this.signupStart = signupStart; }
    public String getSignupEnd() { return signupEnd; }
    public void setSignupEnd(String signupEnd) { this.signupEnd = signupEnd; }
    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }
    public String getSignupEndPrecision() { return signupEndPrecision; }
    public void setSignupEndPrecision(String signupEndPrecision) { this.signupEndPrecision = signupEndPrecision; }
    public String getDateNote() { return dateNote; }
    public void setDateNote(String dateNote) { this.dateNote = dateNote; }
    public Boolean getConfirmDateInterpretation() { return confirmDateInterpretation; }
    public void setConfirmDateInterpretation(Boolean confirmDateInterpretation) { this.confirmDateInterpretation = confirmDateInterpretation; }
    public Boolean getDuplicateAcknowledged() { return duplicateAcknowledged; }
    public void setDuplicateAcknowledged(Boolean duplicateAcknowledged) { this.duplicateAcknowledged = duplicateAcknowledged; }
    public String getDuplicateNote() { return duplicateNote; }
    public void setDuplicateNote(String duplicateNote) { this.duplicateNote = duplicateNote; }
    public String getReviewNote() { return reviewNote; }
    public void setReviewNote(String reviewNote) { this.reviewNote = reviewNote; }
}
