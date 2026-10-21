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
    private String outputTarget;
    private boolean weeklyHoursUnrestricted;
    private boolean windowUnrestricted;
    private String competitionIntentStatus = "NONE";
    private List<Long> resolvedCompetitionIds = new ArrayList<>();
    public String getOutputTarget() { return outputTarget == null ? (Boolean.TRUE.equals(teamOnly) ? "TEAMS" : "BOTH") : outputTarget; }
    public void setOutputTarget(String value) { outputTarget = value; teamOnly = "TEAMS".equals(value); }
    public boolean wantsCompetitions() { return !"TEAMS".equals(getOutputTarget()); }
    public boolean wantsTeams() { return !"COMPETITIONS".equals(getOutputTarget()); }
    public boolean isWeeklyHoursUnrestricted() { return weeklyHoursUnrestricted; }
    public void setWeeklyHoursUnrestricted(boolean value) { weeklyHoursUnrestricted = value; }
    public boolean isWindowUnrestricted() { return windowUnrestricted; }
    public void setWindowUnrestricted(boolean value) { windowUnrestricted = value; }
    public String getCompetitionIntentStatus() { return competitionIntentStatus; }
    public void setCompetitionIntentStatus(String value) { competitionIntentStatus = value == null ? "NONE" : value; }
    public List<Long> getResolvedCompetitionIds() { return resolvedCompetitionIds; }
    public void setResolvedCompetitionIds(List<Long> value) { resolvedCompetitionIds = value == null ? new ArrayList<>() : value; }
    private String note;
    private List<String> competitionKeywords = new ArrayList<>();
    public List<String> getCompetitionKeywords() { return competitionKeywords; }
    public void setCompetitionKeywords(List<String> v) { competitionKeywords = v == null ? new ArrayList<>() : v; }
    private List<Long> excludedCompetitionIds = new ArrayList<>();
    public List<Long> getExcludedCompetitionIds() { return excludedCompetitionIds; }
    public void setExcludedCompetitionIds(List<Long> v) { excludedCompetitionIds = v == null ? new ArrayList<>() : v; }
    private List<Long> excludedTeamIds = new ArrayList<>();
    public List<Long> getExcludedTeamIds() { return excludedTeamIds; }
    public void setExcludedTeamIds(List<Long> v) { excludedTeamIds = v == null ? new ArrayList<>() : v; }

    private Map<String, String> sources = new LinkedHashMap<>();

    public Integer getWeeklyHours() { return weeklyHours; }
    public void setWeeklyHours(Integer weeklyHours) { this.weeklyHours = weeklyHours; if (weeklyHours != null) weeklyHoursUnrestricted = false; }
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
    public void setWindowDays(Integer windowDays) { this.windowDays = windowDays; if (windowDays != null) windowUnrestricted = false; }
    public Boolean getTeamOnly() { return outputTarget == null ? teamOnly : Boolean.valueOf("TEAMS".equals(outputTarget)); }
    public void setTeamOnly(Boolean teamOnly) { this.teamOnly = teamOnly; if (Boolean.TRUE.equals(teamOnly)) outputTarget = "TEAMS"; else if ("TEAMS".equals(outputTarget)) outputTarget = "BOTH"; }
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
        copy.setOutputTarget(getOutputTarget());
        copy.setWeeklyHoursUnrestricted(weeklyHoursUnrestricted);
        copy.setWindowUnrestricted(windowUnrestricted);
        copy.setCompetitionIntentStatus(competitionIntentStatus);
        copy.setResolvedCompetitionIds(new ArrayList<>(resolvedCompetitionIds));
        copy.setNote(note);
        copy.setCompetitionKeywords(new ArrayList<>(competitionKeywords));
        copy.setExcludedCompetitionIds(new ArrayList<>(excludedCompetitionIds));
        copy.setExcludedTeamIds(new ArrayList<>(excludedTeamIds));

        copy.setSources(new LinkedHashMap<>(sources));
        return copy;
    }
}
