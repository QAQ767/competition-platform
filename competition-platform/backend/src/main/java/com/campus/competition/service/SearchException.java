package com.campus.competition.service;

/** 搜索失败。code 区分未配置、鉴权、限流和网络错误。 */
public class SearchException extends Exception {

    private final String code;

    public SearchException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
