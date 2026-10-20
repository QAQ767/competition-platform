package com.campus.competition.service;

import com.campus.competition.config.DiscoveryProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Brave Web Search API 适配器。
 * 文档：https://api-dashboard.search.brave.com/api-reference/web/search/get
 * 未配置密钥时 isConfigured=false，作业会明确失败并保留 URL/文本导入入口。
 */
@Service
public class BraveSearchProvider implements CompetitionSearchProvider {

    private static final Logger log = LoggerFactory.getLogger(BraveSearchProvider.class);

    private final DiscoveryProperties properties;
    private final UrlSafetyPolicy policy;
    private final RestTemplate restTemplate;

    public BraveSearchProvider(DiscoveryProperties properties, UrlSafetyPolicy policy) {
        this.properties = properties;
        this.policy = policy;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(8000);
        factory.setReadTimeout(15000);
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public String name() {
        return "brave";
    }

    @Override
    public boolean isConfigured() {
        return "brave".equalsIgnoreCase(properties.getSearch().getProvider())
                && properties.getSearch().getApiKey() != null
                && !properties.getSearch().getApiKey().isBlank();
    }

    @Override
    public String configStatus() {
        String provider = properties.getSearch().getProvider();
        if (provider == null || provider.isBlank() || "none".equalsIgnoreCase(provider)) {
            return "未启用联网搜索（discovery.search.provider=none）";
        }
        if (!"brave".equalsIgnoreCase(provider)) {
            return "不支持的搜索提供方：" + provider;
        }
        if (!isConfigured()) {
            return "Brave Search 缺少 BRAVE_SEARCH_API_KEY";
        }
        return "Brave Search 已配置";
    }

    @Override
    public List<SearchHit> search(String query, int maxResults) throws SearchException {
        if (!isConfigured()) {
            throw new SearchException("SEARCH_NOT_CONFIGURED", configStatus());
        }
        try {
            String url = UriComponentsBuilder.fromHttpUrl(properties.getSearch().getEndpoint())
                    .queryParam("q", query)
                    .queryParam("count", Math.max(1, Math.min(maxResults, 20)))
                    .queryParam("country", properties.getSearch().getCountry())
                    .queryParam("search_lang", properties.getSearch().getLang())
                    .encode()
                    .build()
                    .toUriString();
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            headers.set("X-Subscription-Token", properties.getSearch().getApiKey());
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            if (response.getBody() == null) {
                throw new SearchException("SEARCH_EMPTY", "搜索返回空响应");
            }
            JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getBody());
            JsonNode results = root.path("web").path("results");
            List<SearchHit> hits = new ArrayList<>();
            if (results.isArray()) {
                for (JsonNode row : results) {
                    if (hits.size() >= maxResults) break;
                    String link = row.path("url").asText("");
                    try {
                        policy.validate(link);
                    } catch (FetchException blocked) {
                        continue;
                    }
                    String title = HtmlTextExtractor.cleanInline(row.path("title").asText(""));
                    if (title.isBlank()) continue;
                    hits.add(new SearchHit(title, link,
                            HtmlTextExtractor.cleanInline(row.path("description").asText(""))));
                }
            }
            return hits;
        } catch (HttpClientErrorException e) {
            int status = e.getRawStatusCode();
            if (status == 401 || status == 403) {
                throw new SearchException("SEARCH_AUTH_FAILED", "搜索 API 鉴权失败，请检查密钥");
            }
            if (status == 429) {
                throw new SearchException("SEARCH_RATE_LIMITED", "搜索 API 限流，请稍后再试");
            }
            throw new SearchException("SEARCH_FAILED", "搜索 API 返回 HTTP " + status);
        } catch (ResourceAccessException e) {
            log.warn("搜索 API 网络失败: {}", e.getMessage());
            throw new SearchException("SEARCH_UNAVAILABLE", "无法连接搜索 API，请检查网络或稍后重试");
        } catch (SearchException e) {
            throw e;
        } catch (Exception e) {
            log.warn("搜索 API 解析失败: {}", e.getMessage());
            throw new SearchException("SEARCH_FAILED", "搜索响应解析失败");
        }
    }
}
