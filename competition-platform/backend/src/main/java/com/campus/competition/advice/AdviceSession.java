package com.campus.competition.advice;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Redis 中保存的会话。只保留最近若干轮对话与已确认需求摘要。 */
public class AdviceSession {

    private String id;
    private Long userId;
    private Integer version = 0;
    private AdvicePreferences preferences = new AdvicePreferences();
    private List<AdviceMessage> messages = new ArrayList<>();
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public AdvicePreferences getPreferences() { return preferences; }
    public void setPreferences(AdvicePreferences preferences) { this.preferences = preferences == null ? new AdvicePreferences() : preferences; }
    public List<AdviceMessage> getMessages() { return messages; }
    public void setMessages(List<AdviceMessage> messages) { this.messages = messages == null ? new ArrayList<>() : messages; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
