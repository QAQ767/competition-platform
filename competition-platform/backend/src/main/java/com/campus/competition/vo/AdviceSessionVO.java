package com.campus.competition.vo;

import com.campus.competition.advice.AdviceMessage;
import com.campus.competition.advice.AdvicePreferences;

import java.util.ArrayList;
import java.util.List;

/** 会话详情：档案摘要 + 已确认需求 + 最近对话。 */
public class AdviceSessionVO {

    private String id;
    private Integer version;
    private AdviceProfileSummaryVO profileSummary;
    private List<String> missingFields = new ArrayList<>();
    private AdvicePreferences preferences;
    private List<AdviceMessage> messages = new ArrayList<>();
    private String createdAt;
    private String updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public AdviceProfileSummaryVO getProfileSummary() { return profileSummary; }
    public void setProfileSummary(AdviceProfileSummaryVO profileSummary) { this.profileSummary = profileSummary; }
    public List<String> getMissingFields() { return missingFields; }
    public void setMissingFields(List<String> missingFields) { this.missingFields = missingFields; }
    public AdvicePreferences getPreferences() { return preferences; }
    public void setPreferences(AdvicePreferences preferences) { this.preferences = preferences; }
    public List<AdviceMessage> getMessages() { return messages; }
    public void setMessages(List<AdviceMessage> messages) { this.messages = messages; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
