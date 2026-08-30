package com.campus.competition.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 修改队伍名请求
 */
public class TeamRenameReq {

    @NotBlank(message = "队伍名不能为空")
    @Size(max = 50, message = "队伍名最长 50 字")
    private String title;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
