package com.campus.competition.dto;

import javax.validation.constraints.NotBlank;

/**
 * 训练资料创建请求（先传文件拿 url，再登记资料）
 */
public class ResourceCreateReq {

    @NotBlank(message = "资料名称不能为空")
    private String title;

    @NotBlank(message = "文件地址不能为空")
    private String url;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
