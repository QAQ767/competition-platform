package com.campus.competition.dto;

import javax.validation.constraints.NotBlank;

/**
 * 任务创建请求
 */
public class TaskCreateReq {

    @NotBlank(message = "任务标题不能为空")
    private String title;

    private String content;
    private Long assigneeId;
    private String deadline;

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
