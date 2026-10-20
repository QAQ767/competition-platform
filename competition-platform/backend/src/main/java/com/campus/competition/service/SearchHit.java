package com.campus.competition.service;

/** 一条搜索命中。只作为发现线索，不能替代官网正文。 */
public class SearchHit {

    private String title;
    private String url;
    private String description;

    public SearchHit() { }

    public SearchHit(String title, String url, String description) {
        this.title = title;
        this.url = url;
        this.description = description;
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
