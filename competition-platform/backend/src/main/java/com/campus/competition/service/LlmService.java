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
import org.springframework.web.client.RestTemplate;


/**
 * 大模型服务（组队分析与内容审核）
 *
 * 支持两种 provider：
 *  - ollama：本地模型（默认 deepseek-r1:7b），完全离线，无需 API Key
 *  - deepseek：云端 DeepSeek API，需 LLM_API_KEY 环境变量
 *
 * 设计：任何异常/超时都返回 null，由调用方（AiService）降级为规则模板，保证推荐功能永不中断。
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

    /**
     * 生成普通文本；任何失败返回 null
     */
    public String generate(String prompt) {
        return generate(prompt, false);
    }

    public String generateAssessment(String prompt) {
        return generate(prompt, true);
    }

    private String generate(String prompt, boolean assessment) {
        if (!properties.isEnabled() || prompt == null) {
            return null;
        }
        try {
            switch (properties.getProvider()) {
                case "ollama":
                    return callOllama(prompt, assessment);
                case "deepseek":
                    return callDeepSeek(prompt);
                default:
                    return null;
            }
        } catch (Exception e) {
            log.warn("LLM 调用失败，降级规则模板: {}", e.getMessage());
            return null;
        }
    }

    /** Ollama /api/chat（本地模型） */
    private String callOllama(String prompt, boolean assessment) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", properties.getModel());
        body.put("stream", false);
        if (assessment) body.set("format", objectMapper.readTree(TeamAiAssessment.SCHEMA));
        ArrayNode messages = body.putArray("messages");
        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", prompt);
        ObjectNode options = body.putObject("options");
        options.put("temperature", 0.2);
        options.put("num_predict", 2400);

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
    private String callDeepSeek(String prompt) throws Exception {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            log.warn("DeepSeek 未配置 LLM_API_KEY，降级规则模板");
            return null;
        }
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", properties.getModel().isBlank() ? "deepseek-chat" : properties.getModel());
        body.put("stream", false);
        body.put("temperature", 0.7);
        body.put("max_tokens", 2400);
        ArrayNode messages = body.putArray("messages");
        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", prompt);

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
