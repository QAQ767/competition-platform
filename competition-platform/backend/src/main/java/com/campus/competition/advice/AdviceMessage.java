package com.campus.competition.advice;

import java.util.ArrayList;
import java.util.List;

/** 会话中的一条消息。changes/questions 只保存结构化摘要，不保存模型思考过程。 */
public class AdviceMessage {

    private String role;
    private String content;
    private String at;
    private List<String> changes = new ArrayList<>();
    private List<String> questions = new ArrayList<>();

    public AdviceMessage() { }

    public AdviceMessage(String role, String content, String at) {
        this.role = role;
        this.content = content;
        this.at = at;
    }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getAt() { return at; }
    public void setAt(String at) { this.at = at; }
    public List<String> getChanges() { return changes; }
    public void setChanges(List<String> changes) { this.changes = changes == null ? new ArrayList<>() : changes; }
    public List<String> getQuestions() { return questions; }
    public void setQuestions(List<String> questions) { this.questions = questions == null ? new ArrayList<>() : questions; }
}
