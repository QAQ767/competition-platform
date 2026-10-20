package com.campus.competition.vo;

import com.campus.competition.advice.AdvicePreferences;
import com.campus.competition.advice.PreferenceChange;

import java.util.ArrayList;
import java.util.List;

/** 发送一条对话后的结果。 */
public class AdviceMessageResultVO {

    private String sessionId;
    private Integer version;
    private String reply;
    private String mode;
    private String fallbackReason;
    private List<PreferenceChange> changes = new ArrayList<>();
    private List<String> questions = new ArrayList<>();
    private AdvicePreferences preferences;
    private String at;

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getReply() { return reply; }
    public void setReply(String reply) { this.reply = reply; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getFallbackReason() { return fallbackReason; }
    public void setFallbackReason(String fallbackReason) { this.fallbackReason = fallbackReason; }
    public List<PreferenceChange> getChanges() { return changes; }
    public void setChanges(List<PreferenceChange> changes) { this.changes = changes; }
    public List<String> getQuestions() { return questions; }
    public void setQuestions(List<String> questions) { this.questions = questions; }
    public AdvicePreferences getPreferences() { return preferences; }
    public void setPreferences(AdvicePreferences preferences) { this.preferences = preferences; }
    public String getAt() { return at; }
    public void setAt(String at) { this.at = at; }
}
