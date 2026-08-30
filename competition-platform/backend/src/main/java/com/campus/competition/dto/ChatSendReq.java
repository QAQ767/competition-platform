package com.campus.competition.dto;

import javax.validation.constraints.NotBlank;

/**
 * 发送聊天消息请求
 */
public class ChatSendReq {

    @NotBlank(message = "消息内容不能为空")
    private String content;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
