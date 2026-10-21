package com.campus.competition.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 学生参赛推荐会话参数。 */
@Component
@ConfigurationProperties(prefix = "advice")
public class AdviceProperties {

    /** 会话 TTL（小时）。 */
    private long sessionTtlHours = 24;
    /** 保留的最近消息条数（10 轮 = 20 条）。 */
    private int maxMessages = 20;
    /** 单条用户消息最大长度。 */
    private int maxMessageLength = 2000;
    /** 单个用户每分钟最多发消息/生成推荐的次数。 */
    private int maxRequestsPerMinute = 20;
    /** 默认时间窗口（天）。 */
    private int defaultWindowDays = 90;
    /** 允许的时间窗口范围。 */
    private int minWindowDays = 7;
    private int maxWindowDays = 365;
    private long turnTimeoutSeconds = 90;
    public long getTurnTimeoutSeconds() { return turnTimeoutSeconds; }
    public void setTurnTimeoutSeconds(long seconds) { turnTimeoutSeconds = Math.max(1, Math.min(90, seconds)); }

    public long getSessionTtlHours() {
        return sessionTtlHours;
    }

    public void setSessionTtlHours(long sessionTtlHours) {
        this.sessionTtlHours = sessionTtlHours;
    }

    public int getMaxMessages() {
        return maxMessages;
    }

    public void setMaxMessages(int maxMessages) {
        this.maxMessages = maxMessages;
    }

    public int getMaxMessageLength() {
        return maxMessageLength;
    }

    public void setMaxMessageLength(int maxMessageLength) {
        this.maxMessageLength = maxMessageLength;
    }

    public int getMaxRequestsPerMinute() {
        return maxRequestsPerMinute;
    }

    public void setMaxRequestsPerMinute(int maxRequestsPerMinute) {
        this.maxRequestsPerMinute = maxRequestsPerMinute;
    }

    public int getDefaultWindowDays() {
        return defaultWindowDays;
    }

    public void setDefaultWindowDays(int defaultWindowDays) {
        this.defaultWindowDays = defaultWindowDays;
    }

    public int getMinWindowDays() {
        return minWindowDays;
    }

    public void setMinWindowDays(int minWindowDays) {
        this.minWindowDays = minWindowDays;
    }

    public int getMaxWindowDays() {
        return maxWindowDays;
    }

    public void setMaxWindowDays(int maxWindowDays) {
        this.maxWindowDays = maxWindowDays;
    }
}
