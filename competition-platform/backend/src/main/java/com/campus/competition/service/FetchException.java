package com.campus.competition.service;

/** 抓取失败。code 用于前端区分配置、网络、来源问题。 */
public class FetchException extends Exception {

    private final String code;

    public FetchException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
