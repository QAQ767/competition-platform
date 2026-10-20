package com.campus.competition.vo;

/** 搜索/模型配置状态。只返回是否可用，不返回密钥。 */
public class DiscoveryStatusVO {

    private boolean searchConfigured;
    private String searchProvider;
    private String searchStatus;
    private boolean modelConfigured;
    private String modelStatus;
    private int fetchTimeoutSeconds;
    private int maxResults;
    /** 整个发现作业的总时限（秒）；超时后不再开始新的抓取/提取。 */
    private long jobTimeoutSeconds;

    public boolean isSearchConfigured() { return searchConfigured; }
    public void setSearchConfigured(boolean searchConfigured) { this.searchConfigured = searchConfigured; }
    public String getSearchProvider() { return searchProvider; }
    public void setSearchProvider(String searchProvider) { this.searchProvider = searchProvider; }
    public String getSearchStatus() { return searchStatus; }
    public void setSearchStatus(String searchStatus) { this.searchStatus = searchStatus; }
    public boolean isModelConfigured() { return modelConfigured; }
    public void setModelConfigured(boolean modelConfigured) { this.modelConfigured = modelConfigured; }
    public String getModelStatus() { return modelStatus; }
    public void setModelStatus(String modelStatus) { this.modelStatus = modelStatus; }
    public int getFetchTimeoutSeconds() { return fetchTimeoutSeconds; }
    public void setFetchTimeoutSeconds(int fetchTimeoutSeconds) { this.fetchTimeoutSeconds = fetchTimeoutSeconds; }
    public int getMaxResults() { return maxResults; }
    public void setMaxResults(int maxResults) { this.maxResults = maxResults; }
    public long getJobTimeoutSeconds() { return jobTimeoutSeconds; }
    public void setJobTimeoutSeconds(long jobTimeoutSeconds) { this.jobTimeoutSeconds = jobTimeoutSeconds; }
}
