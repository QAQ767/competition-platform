package com.campus.competition.advice;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话中已确认的临时需求。只覆盖用户明确表达的内容；
 * sources 记录每个字段的来源：PROFILE / DIALOGUE / MANUAL。
 */
public class AdvicePreferences {

    public static final String SOURCE_PROFILE = "PROFILE";
    public static final String SOURCE_DIALOGUE = "DIALOGUE";
    public static final String SOURCE_MANUAL = "MANUAL";

    private Integer weeklyHours;
    private List<String> directions = new ArrayList<>();
    private List<String> skills = new ArrayList<>();
    private List<String> roles = new ArrayList<>();
    private String difficulty;
    private List<String> excludedKeywords = new ArrayList<>();
    private Integer windowDays;
    private Boolean teamOnly;
    private String note;
    private Map<String, String> sources = new LinkedHashMap<>();

    public Integer getWeeklyHours() { return weeklyHours; }
    public void setWeeklyHours(Integer weeklyHours) { this.weeklyHours = weeklyHours; }
    public List<String> getDirections() { return directions; }
    public void setDirections(List<String> directions) { this.directions = directions == null ? new ArrayList<>() : directions; }
    public List<String> getSkills() { return skills; }
    public void setSkills(List<String> skills) { this.skills = skills == null ? new ArrayList<>() : skills; }
    public List<String> getRoles() { return roles; }
    public void setRoles(List<String> roles) { this.roles = roles == null ? new ArrayList<>() : roles; }
    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
    public List<String> getExcludedKeywords() { return excludedKeywords; }
    public void setExcludedKeywords(List<String> excludedKeywords) { this.excludedKeywords = excludedKeywords == null ? new ArrayList<>() : excludedKeywords; }
    public Integer getWindowDays() { return windowDays; }
    public void setWindowDays(Integer windowDays) { this.windowDays = windowDays; }
    public Boolean getTeamOnly() { return teamOnly; }
    public void setTeamOnly(Boolean teamOnly) { this.teamOnly = teamOnly; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Map<String, String> getSources() { return sources; }
    public void setSources(Map<String, String> sources) { this.sources = sources == null ? new LinkedHashMap<>() : sources; }

    public String sourceOf(String field) {
        return sources.getOrDefault(field, SOURCE_PROFILE);
    }

    public void markSource(String field, String source) {
        sources.put(field, source);
    }

    public boolean hasDialogueOverride() {
        return sources.values().stream().anyMatch(s -> !SOURCE_PROFILE.equals(s));
    }

    public AdvicePreferences copy() {
        AdvicePreferences copy = new AdvicePreferences();
        copy.setWeeklyHours(weeklyHours);
        copy.setDirections(new ArrayList<>(directions));
        copy.setSkills(new ArrayList<>(skills));
        copy.setRoles(new ArrayList<>(roles));
        copy.setDifficulty(difficulty);
        copy.setExcludedKeywords(new ArrayList<>(excludedKeywords));
        copy.setWindowDays(windowDays);
        copy.setTeamOnly(teamOnly);
        copy.setNote(note);
        copy.setSources(new LinkedHashMap<>(sources));
        return copy;
    }
}
