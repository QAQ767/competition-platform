package com.campus.competition.dto;

import javax.validation.constraints.NotBlank;

/**
 * 任务状态更新请求
 */
public class TaskStatusReq {

    @NotBlank(message = "任务状态不能为空")
    private String status;

    @javax.validation.constraints.Size(max = 1000, message = "复盘不能超过1000字")
    private String completionNote;

    public String getCompletionNote() { return completionNote; }
    public void setCompletionNote(String completionNote) { this.completionNote = completionNote; }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
