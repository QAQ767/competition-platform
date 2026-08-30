package com.campus.competition.dto;

import javax.validation.constraints.NotBlank;

/**
 * 反馈提交请求
 */
public class FeedbackReq {

    @NotBlank(message = "请选择反馈类型")
    private String type;

    @NotBlank(message = "反馈内容不能为空")
    private String content;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
