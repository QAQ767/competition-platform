package com.campus.competition.dto;

import javax.validation.constraints.NotBlank;

/**
 * 任务创建请求
 */
public class TaskCreateReq {

    @NotBlank(message = "任务标题不能为空")
    @javax.validation.constraints.Size(max = 100, message = "标题不能超过100字")
    private String title;

    @javax.validation.constraints.Size(max = 500, message = "说明不能超过500字")
    private String content;
    private Long assigneeId;
    private String deadline;

    private String phase;
    private String priority;
    private Long resourceId;

    public String getPhase() { return phase; }
    public void setPhase(String phase) { this.phase = phase; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public Long getResourceId() { return resourceId; }
    public void setResourceId(Long resourceId) { this.resourceId = resourceId; }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Long getAssigneeId() {
        return assigneeId;
    }

    public void setAssigneeId(Long assigneeId) {
        this.assigneeId = assigneeId;
    }

    public String getDeadline() {
        return deadline;
    }

    public void setDeadline(String deadline) {
        this.deadline = deadline;
    }
}
