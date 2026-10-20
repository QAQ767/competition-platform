package com.campus.competition.dto;

/** 启动一次赛事发现作业。mode: SEARCH / URL / TEXT。 */
public class DiscoveryJobReq {

    private String mode;
    private String keyword;
    private String direction;
    private String region;
    private Integer windowDays;
    private Integer maxResults;
    private String url;
    private String text;
    private String pageTitle;

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public Integer getWindowDays() { return windowDays; }
    public void setWindowDays(Integer windowDays) { this.windowDays = windowDays; }
    public Integer getMaxResults() { return maxResults; }
    public void setMaxResults(Integer maxResults) { this.maxResults = maxResults; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getPageTitle() { return pageTitle; }
    public void setPageTitle(String pageTitle) { this.pageTitle = pageTitle; }
}
