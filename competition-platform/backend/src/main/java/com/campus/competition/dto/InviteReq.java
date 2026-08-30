package com.campus.competition.dto;

import javax.validation.constraints.NotNull;

/**
 * 邀请入队请求
 */
public class InviteReq {

    @NotNull(message = "请选择要邀请的用户")
    private Long userId;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}
