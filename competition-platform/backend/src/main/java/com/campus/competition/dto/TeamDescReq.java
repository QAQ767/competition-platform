package com.campus.competition.dto;

/**
 * 修改队伍简介请求（可为空字符串表示清空简介）
 */
public class TeamDescReq {

    private String description;

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
