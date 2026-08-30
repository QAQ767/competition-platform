package com.campus.competition.dto;

import javax.validation.constraints.NotBlank;

/**
 * 刷新令牌请求
 */
public class RefreshReq {

    @NotBlank(message = "refreshToken 不能为空")
    private String refreshToken;

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}
