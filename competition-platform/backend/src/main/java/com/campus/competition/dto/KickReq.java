package com.campus.competition.dto;

import javax.validation.constraints.NotBlank;

/**
 * 踢出队员请求（理由必填）
 */
public class KickReq {

    @NotBlank(message = "请填写踢出理由")
    private String reason;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
