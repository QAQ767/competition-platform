package com.campus.competition.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 管理员“近期赛事发现”配置。密钥只从环境变量注入，不下发浏览器。 */
@Component
@ConfigurationProperties(prefix = "discovery")
public class DiscoveryProperties {

    private final Search search = new Search();
    private final Fetch fetch = new Fetch();
    private final Job job = new Job();

    public Search getSearch() {
        return search;
    }

    public Fetch getFetch() {
        return fetch;
    }

    public Job getJob() {
        return job;
    }

    public static class Search {
        /** none / brave */
        private String provider = "none";
        private String apiKey = "";
        private String endpoint = "https://api.search.brave.com/res/v1/web/search";
        private String country = "CN";
        private String lang = "zh-hans";

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getCountry() {
            return country;
        }

        public void setCountry(String country) {
            this.country = country;
        }

        public String getLang() {
            return lang;
        }

        public void setLang(String lang) {
            this.lang = lang;
        }
    }

    public static class Fetch {
        private int timeoutSeconds = 15;
        private int maxBytes = 2 * 1024 * 1024;
        private int maxRedirects = 3;
        private String userAgent = "CampusCompetitionDiscovery/1.0 (+admin-triggered)";
        /**
         * 仅用于本机代理的 fake-IP 环境（DNS 返回 198.18.0.0/15）。
         * 默认 false；生产环境必须保持 false，否则会削弱 SSRF 防护。
         */
        private boolean allowFakeIp = false;

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        public int getMaxBytes() {
            return maxBytes;
        }

        public void setMaxBytes(int maxBytes) {
            this.maxBytes = maxBytes;
        }

        public int getMaxRedirects() {
            return maxRedirects;
        }

        public void setMaxRedirects(int maxRedirects) {
            this.maxRedirects = maxRedirects;
        }

        public String getUserAgent() {
            return userAgent;
        }

        public void setUserAgent(String userAgent) {
            this.userAgent = userAgent;
        }

        public boolean isAllowFakeIp() {
            return allowFakeIp;
        }

        public void setAllowFakeIp(boolean allowFakeIp) {
            this.allowFakeIp = allowFakeIp;
        }
    }

    public static class Job {
        private int maxResults = 10;
        private int concurrency = 2;
        /** 整个作业的总时限（秒）。单次抓取的 socket 读超时不能替代它。 */
        private long timeoutSeconds = 300;

        public int getMaxResults() {
            return maxResults;
        }

        public void setMaxResults(int maxResults) {
            this.maxResults = maxResults;
        }

        public int getConcurrency() {
            return concurrency;
        }

        public void setConcurrency(int concurrency) {
            this.concurrency = concurrency;
        }

        public long getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(long timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }
}
