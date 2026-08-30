package com.campus.competition.dto;

import javax.validation.constraints.NotBlank;

/**
 * 任务状态更新请求
 */
public class TaskStatusReq {

    @NotBlank(message = "任务状态不能为空")
    private String status;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
