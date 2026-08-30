package com.campus.competition.dto;

import javax.validation.constraints.NotBlank;

/**
 * 参赛状态更新请求
 */
public class CompeteStatusReq {

    @NotBlank(message = "参赛状态不能为空")
    private String competeStatus;

    public String getCompeteStatus() {
        return competeStatus;
    }

    public void setCompeteStatus(String competeStatus) {
        this.competeStatus = competeStatus;
    }
}
