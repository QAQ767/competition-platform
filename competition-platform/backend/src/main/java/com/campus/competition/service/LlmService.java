package com.campus.competition.service;

import com.campus.competition.config.LlmProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

/**
 * 大模型服务（组队分析、参赛推荐、赛事信息提取）
 *
 * 支持 ollama（本地）与 deepseek（云端）。结构化调用把 JSON Schema 交给 Ollama 的
 * format 参数，DeepSeek 则使用 json_object 模式；无论哪种方式，返回值都必须由调用方
 * 再做业务校验。任何失败都返回带原因的 {@link LlmResult}，不抛出到 Controller。
 */
@Service
public class LlmService {

    private static final Logger log = LoggerFactory.getLogger(LlmService.class);

    private final LlmProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public LlmService(LlmProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout((int) properties.getTimeoutSeconds() * 1000);
        this.restTemplate = new RestTemplate(factory);
    }

    /** 调用结果。error 为稳定的错误码，message 可直接展示，但不含密钥和堆栈。 */
    public static final class LlmResult {
        private final boolean ok;
        private final String content;
        private final String error;
        private final String message;

        private LlmResult(boolean ok, String content, String error, String message) {
            this.ok = ok;
            this.content = content;
            this.error = error;
            this.message = message;
        }

        public static LlmResult ok(String content) {
            return new LlmResult(true, content, null, null);
        }

        public static LlmResult fail(String error, String message) {
            return new LlmResult(false, null, error, message);
        }

        public boolean isOk() {
            return ok;
        }

        public String getContent() {
            return content;
        }

        public String getError() {
            return error;
        }

        public String getMessage() {
            return message;
        }
    }

    /**
     * 生成普通文本；任何失败返回 null（保留旧调用方的降级语义）。
     */
    public String generate(String prompt) {
        LlmResult result = generateResult(prompt, null, 1600);
        return result.isOk() ? result.getContent() : null;
    }

    /** 组队分析旧的 Schema 调用入口，保持原有行为。 */
    public String generateAssessment(String prompt) {
        LlmResult result = generateResult(prompt, TeamAiAssessment.SCHEMA, 2400);
        return result.isOk() ? result.getContent() : null;
    }

    /** 通用结构化调用。schema 为空时退化为普通文本。 */
    public LlmResult generateStructured(String prompt, String schema, int maxTokens) {
        return generateResult(prompt, schema, maxTokens);
    }

    /** 通用文本调用，带明确失败原因。 */
    public LlmResult generateText(String prompt, int maxTokens) {
        return generateResult(prompt, null, maxTokens);
    }

    private LlmResult generateResult(String prompt, String schema, int maxTokens) {
        if (prompt == null || prompt.isBlank()) {
            return LlmResult.fail("EMPTY_PROMPT", "提示词为空");
        }
        if (!properties.isEnabled()) {
            return LlmResult.fail("LLM_DISABLED", "模型已关闭");
        }
        if (!properties.isConfigured()) {
            return LlmResult.fail("LLM_NOT_CONFIGURED", properties.configStatus());
        }
        try {
            String content = switch (properties.getProvider().toLowerCase()) {
                case "ollama" -> callOllama(prompt, schema, maxTokens);
                case "deepseek" -> callDeepSeek(prompt, schema, maxTokens);
                default -> null;
            };
            if (content == null || content.isBlank()) {
                return LlmResult.fail("EMPTY_RESPONSE", "模型未返回内容");
            }
            return LlmResult.ok(content);
        } catch (ResourceAccessException e) {
            String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
            if (message.contains("timed out") || message.contains("timeout")) {
                log.warn("LLM 调用超时，降级规则结果");
                return LlmResult.fail("LLM_TIMEOUT", "模型调用超时");
            }
            log.warn("LLM 连接失败，降级规则结果: {}", e.getMessage());
            return LlmResult.fail("LLM_UNAVAILABLE", "无法连接模型服务");
        } catch (Exception e) {
            log.warn("LLM 调用失败，降级规则结果: {}", e.getMessage());
            return LlmResult.fail("LLM_FAILED", "模型调用失败");
        }
    }

    /** Ollama /api/chat（本地模型） */
    private String callOllama(String prompt, String schema, int maxTokens) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", properties.getModel());
        body.put("stream", false);
        // 结构化任务不需要展示思考过程；thinking 模型（如 deepseek-r1）可显著降低延迟。
        if (schema != null && !schema.isBlank()) {
            body.put("think", false);
            body.set("format", objectMapper.readTree(schema));
        }
        ArrayNode messages = body.putArray("messages");
        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", prompt);
        ObjectNode options = body.putObject("options");
        options.put("temperature", 0.2);
        options.put("num_predict", Math.max(256, maxTokens));
        // 推荐/提取提示词包含多个候选或公告正文，默认 2048 上下文会截断输入。
        options.put("num_ctx", 8192);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);

        String url = trimSlash(properties.getBaseUrl()) + "/api/chat";
        String response = restTemplate.postForObject(url, request, String.class);
        JsonNode root = objectMapper.readTree(response);
        JsonNode content = root.path("message").path("content");
        return content.isMissingNode() || content.isNull() ? null : content.asText().trim();
    }

    /** DeepSeek 云端 API /chat/completions（需 API Key） */
    private String callDeepSeek(String prompt, String schema, int maxTokens) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", properties.getModel().isBlank() ? "deepseek-chat" : properties.getModel());
        body.put("stream", false);
        body.put("temperature", 0.2);
        body.put("max_tokens", Math.max(256, maxTokens));
        if (schema != null && !schema.isBlank()) {
            ObjectNode format = body.putObject("response_format");
            format.put("type", "json_object");
        }
        String finalPrompt = schema == null || schema.isBlank()
                ? prompt
                : prompt + "\n只输出符合要求的 JSON，不要 Markdown 代码块或额外说明。";
        ArrayNode messages = body.putArray("messages");
        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", finalPrompt);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(properties.getApiKey());
        HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);

        String url = trimSlash(properties.getBaseUrl()) + "/chat/completions";
        String response = restTemplate.postForObject(url, request, String.class);
        JsonNode root = objectMapper.readTree(response);
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        return content.isMissingNode() || content.isNull() ? null : content.asText().trim();
    }

    private String trimSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
