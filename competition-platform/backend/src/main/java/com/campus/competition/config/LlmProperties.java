package com.campus.competition.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 大模型配置（LLM）
 * provider: ollama（本地） / deepseek（云端 API） / none（关闭，仅规则引擎）
 */
@Component
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {

    /** 是否启用大模型 */
    private boolean enabled = true;

    /** ollama / deepseek / none */
    private String provider = "none";

    /** API 地址（ollama 默认本地 11434；deepseek 为 https://api.deepseek.com） */
    private String baseUrl = "http://localhost:11434";

    /** 模型名 */
    private String model = "deepseek-r1:7b";

    /** 云端 API Key（deepseek 必填，通过环境变量 LLM_API_KEY 注入） */
    private String apiKey = "";

    /** 单次调用超时（秒），超时自动降级规则引擎 */
    private long timeoutSeconds = 15;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public long getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(long timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }
}
