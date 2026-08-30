package com.campus.competition.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 内容审查服务（脏话过滤）
 *
 * 两层审查：
 *  1. 规则词库：命中即拦截（即时、确定）
 *  2. AI 审查：包含"边界词"（如 蠢/滚/猪/垃圾 等模棱两可的词）时调用本地大模型
 *     判断是否辱骂，结果缓存 1 小时避免重复审查；LLM 不可用时按规则词库结果放行
 */
@Service
public class ContentFilterService {

    private static final Logger log = LoggerFactory.getLogger(ContentFilterService.class);

    /** 敏感词库：命中直接拦截 */
    private static final List<String> SENSITIVE_WORDS = List.of(
            "妈的", "妈逼", "傻逼", "煞笔", "你妈", "草泥马", "操你", "操你妈", "去你妈",
            "王八蛋", "狗日的", "他妈的", "日你", "贱人", "婊子", "妓女", "鸡巴", "傻屌",
            "白痴", "蠢猪", "滚蛋", "去死", "废物", "废物点心", "杂种", "畜生", "狗杂种",
            "脑残", "弱智", "麻痹", "装逼", "尼玛", "卧槽尼玛", "你麻痹"
    );

    /** 边界词：命中后交给 AI 判断 */
    private static final List<String> BORDERLINE_WORDS = List.of(
            "蠢", "笨", "滚", "狗", "猪", "垃圾", "傻", "呆", "恶心", "讨厌", "闭嘴", "滚开"
    );

    private static final String CACHE_PREFIX = "filter:msg:";

    private final LlmService llmService;
    private final CacheService cacheService;

    public ContentFilterService(LlmService llmService, CacheService cacheService) {
        this.llmService = llmService;
        this.cacheService = cacheService;
    }

    /**
     * 内容是否干净（不包含脏话）
     */
    public boolean isClean(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        // 第一层：规则词库
        for (String word : SENSITIVE_WORDS) {
            if (text.contains(word)) {
                return false;
            }
        }
        // 第二层：边界词交给 AI 审查（带缓存）
        boolean hasBorderline = BORDERLINE_WORDS.stream().anyMatch(text::contains);
        if (hasBorderline) {
            return aiCheck(text);
        }
        return true;
    }

    private boolean aiCheck(String text) {
        String cacheKey = CACHE_PREFIX + Integer.toHexString(text.hashCode());
        String cached = cacheService.get(cacheKey, String.class);
        if ("PASS".equals(cached)) {
            return true;
        }
        if ("BLOCK".equals(cached)) {
            return false;
        }
        String answer = llmService.generate(
                "你是网络内容安全审查员。请判断以下聊天消息是否包含辱骂、脏话或不文明用语。"
                        + "只回答 YES（包含）或 NO（不包含），不要输出其他内容。\n消息：" + text);
        boolean clean = answer != null && answer.toUpperCase().contains("NO");
        cacheService.put(cacheKey, clean ? "PASS" : "BLOCK", 3600);
        if (!clean) {
            log.info("AI 审查拦截不文明消息: {}", text);
        }
        return clean;
    }
}
