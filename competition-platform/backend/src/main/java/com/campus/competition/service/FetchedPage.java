package com.campus.competition.service;

import java.time.LocalDateTime;

/** 实际取回的网页正文。text 已去除脚本和标签，只保留可读文本。 */
public class FetchedPage {

    private String url;
    private String finalUrl;
    private String title;
    private String text;
    private String sourceType;
    private int statusCode;
    private LocalDateTime fetchedAt;

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getFinalUrl() { return finalUrl; }
    public void setFinalUrl(String finalUrl) { this.finalUrl = finalUrl; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public int getStatusCode() { return statusCode; }
    public void setStatusCode(int statusCode) { this.statusCode = statusCode; }
    public LocalDateTime getFetchedAt() { return fetchedAt; }
    public void setFetchedAt(LocalDateTime fetchedAt) { this.fetchedAt = fetchedAt; }
}
