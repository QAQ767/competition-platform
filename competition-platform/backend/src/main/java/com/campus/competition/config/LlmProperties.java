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
    private long timeoutSeconds = 180;

    /** 小智训练问答超时（秒） */
    private long assistantTimeoutSeconds = 120;

    /** 小智单次回答最大生成 token 数 */
    private int assistantMaxTokens = 1200;

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

    public long getAssistantTimeoutSeconds() {
        return assistantTimeoutSeconds;
    }

    public void setAssistantTimeoutSeconds(long assistantTimeoutSeconds) {
        this.assistantTimeoutSeconds = assistantTimeoutSeconds;
    }

    public int getAssistantMaxTokens() {
        return assistantMaxTokens;
    }

    public void setAssistantMaxTokens(int assistantMaxTokens) {
        this.assistantMaxTokens = assistantMaxTokens;
    }

    /**
     * 是否具备实际调用条件。enabled 只表示“允许调用”，provider=none 或云端缺少 key
     * 时仍不能调用，调用方必须据此显示降级原因，而不是假装模型已分析。
     */
    public boolean isConfigured() {
        if (!enabled) return false;
        if ("ollama".equalsIgnoreCase(provider)) {
            return baseUrl != null && !baseUrl.isBlank() && model != null && !model.isBlank();
        }
        if ("deepseek".equalsIgnoreCase(provider)) {
            return apiKey != null && !apiKey.isBlank()
                    && baseUrl != null && !baseUrl.isBlank() && model != null && !model.isBlank();
        }
        return false;
    }

    /** 不包含密钥的配置状态说明，可安全展示给管理员。 */
    public String configStatus() {
        if (!enabled) return "模型已关闭（LLM_ENABLED=false）";
        if ("none".equalsIgnoreCase(provider)) return "模型未配置（provider=none）";
        if ("deepseek".equalsIgnoreCase(provider) && (apiKey == null || apiKey.isBlank())) {
            return "云端模型缺少 LLM_API_KEY";
        }
        if (!isConfigured()) return "模型配置不完整";
        return "模型已配置：" + provider + " / " + model;
    }
}
