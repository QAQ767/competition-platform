package com.campus.competition.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 从公告正文提取出的赛事信息，尚未写入草稿表。 */
public class CompetitionExtraction {

    private String name;
    private String organizer;
    private Integer year;
    private String edition;
    private String track;
    private String level;
    private String description;
    private List<String> tags = new ArrayList<>();
    private String eligibilityText;
    private LocalDateTime signupStart;
    private LocalDateTime signupEnd;
    private LocalDateTime startTime;
    private String signupEndPrecision = "UNKNOWN";
    private String dateNote;
    private Map<String, String> evidence = new LinkedHashMap<>();
    private List<String> missing = new ArrayList<>();
    private List<String> conflicts = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
    private String mode;
    private String fallbackReason;

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
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
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
    public Map<String, String> getEvidence() { return evidence; }
    public void setEvidence(Map<String, String> evidence) { this.evidence = evidence; }
    public List<String> getMissing() { return missing; }
    public void setMissing(List<String> missing) { this.missing = missing; }
    public List<String> getConflicts() { return conflicts; }
    public void setConflicts(List<String> conflicts) { this.conflicts = conflicts; }
    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> warnings) { this.warnings = warnings; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getFallbackReason() { return fallbackReason; }
    public void setFallbackReason(String fallbackReason) { this.fallbackReason = fallbackReason; }
}
