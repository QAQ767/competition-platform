package com.campus.competition.dto;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;

/** 小智训练助手请求。对话记录由浏览器携带，服务端只取最近若干轮。 */
public class TrainingAssistantReq {
    @NotBlank(message = "请输入学习问题")
    @Size(max = 2000, message = "问题最长 2000 字")
    private String message;

    @Valid
    @Size(max = 20, message = "对话上下文最多 20 条")
    private List<ChatTurn> history = new ArrayList<>();

    @Size(max = 50, message = "竞赛分类最长 50 字")
    private String competition;
    @Size(max = 50, message = "技能标签最长 50 字")
    private String tag;
    @Size(max = 100, message = "资源名称最长 100 字")
    private String resourceName;

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public List<ChatTurn> getHistory() { return history; }
    public void setHistory(List<ChatTurn> history) { this.history = history; }
    public String getCompetition() { return competition; }
    public void setCompetition(String competition) { this.competition = competition; }
    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; }
    public String getResourceName() { return resourceName; }
    public void setResourceName(String resourceName) { this.resourceName = resourceName; }

    public static class ChatTurn {
        @NotBlank
        @Size(max = 10)
        private String role;
        @NotBlank
        @Size(max = 3000)
        private String content;

        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }
}
