package com.campus.competition.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 轻量文本信号工具：把技能、角色、赛事标签映射到同一组方向词。
 * 这里只做“弱匹配证据”，不把关键词推断当成官方资格。
 */
public final class TextSignals {

    /** 规范方向词 -> 同义词/关键词。顺序即展示顺序。 */
    private static final Map<String, List<String>> DIRECTIONS = new LinkedHashMap<>();

    static {
        DIRECTIONS.put("前端", List.of("前端", "vue", "react", "javascript", "typescript", "html", "css", "小程序", "web前端"));
        DIRECTIONS.put("后端", List.of("后端", "java", "spring", "node", "golang", "go语言", "python后端", "django", "flask", "服务端", "api开发", "数据库", "mysql", "redis"));
        DIRECTIONS.put("算法", List.of("算法", "acm", "icpc", "蓝桥杯", "leetcode", "数据结构", "竞赛编程", "c++"));
        DIRECTIONS.put("数据分析", List.of("数据分析", "数据科学", "统计", "可视化", "pandas", "sql", "数据挖掘", "bi"));
        DIRECTIONS.put("机器学习", List.of("机器学习", "深度学习", "人工智能", "ai", "模型训练", "pytorch", "tensorflow", "神经网络", "大模型"));
        DIRECTIONS.put("建模", List.of("数学建模", "建模", "运筹", "优化模型", "matlab", "微分方程"));
        DIRECTIONS.put("UI设计", List.of("ui", "交互设计", "视觉设计", "用户体验", "ux", "原型", "figma", "设计"));
        DIRECTIONS.put("文档写作", List.of("文档", "写作", "论文", "商业计划书", "文案", "报告", "排版"));
        DIRECTIONS.put("答辩演讲", List.of("答辩", "演讲", "路演", "汇报", "展示", "宣讲"));
        DIRECTIONS.put("网络安全", List.of("网络安全", "ctf", "渗透", "攻防", "安全", "逆向", "pwn", "web安全"));
        DIRECTIONS.put("硬件", List.of("硬件", "嵌入式", "电子", "单片机", "fpga", "电路", "机器人", "物联网", "传感器"));
        DIRECTIONS.put("测试", List.of("测试", "qa", "自动化测试", "软件测试", "质量保证"));
        DIRECTIONS.put("产品", List.of("产品经理", "产品设计", "需求分析", "产品策划", "原型设计"));
        DIRECTIONS.put("运营", List.of("运营", "市场", "推广", "新媒体", "用户增长"));
    }

    private TextSignals() {
    }

    /** 规范方向词集合，供规则提取器与前端标签使用。 */
    public static Set<String> directionNames() {
        return java.util.Collections.unmodifiableSet(DIRECTIONS.keySet());
    }

    /** 从任意文本中抽取规范方向词；未命中的词不会补造。 */
    public static Set<String> capabilities(String... texts) {
        Set<String> result = new LinkedHashSet<>();
        if (texts == null) return result;
        for (String text : texts) {
            if (text == null || text.isBlank()) continue;
            String lower = text.toLowerCase(Locale.ROOT);
            for (Map.Entry<String, List<String>> entry : DIRECTIONS.entrySet()) {
                for (String alias : entry.getValue()) {
                    if (lower.contains(alias)) {
                        result.add(entry.getKey());
                        break;
                    }
                }
            }
        }
        return result;
    }

    /** 只判断一个文本是否直接出现某个规范方向词（含同义词），用于排除项精确匹配。 */
    public static boolean mentions(String text, String direction) {
        if (text == null || direction == null) return false;
        List<String> aliases = DIRECTIONS.get(direction);
        if (aliases == null) return text.contains(direction);
        String lower = text.toLowerCase(Locale.ROOT);
        return aliases.stream().anyMatch(lower::contains);
    }

    /** 把用户写的排除词映射到规范方向词；无法识别的词保留原文，便于按名称过滤。 */
    public static String canonicalDirection(String token) {
        if (token == null || token.isBlank()) return null;
        String lower = token.toLowerCase(Locale.ROOT).trim();
        for (Map.Entry<String, List<String>> entry : DIRECTIONS.entrySet()) {
            for (String alias : entry.getValue()) {
                if (lower.contains(alias) || alias.contains(lower)) return entry.getKey();
            }
        }
        return token.trim();
    }

    public static List<String> splitTags(String value) {
        List<String> result = new ArrayList<>();
        if (value == null || value.isBlank()) return result;
        for (String part : value.split("[,，、;；/]")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty() && !result.contains(trimmed) && result.size() < 20) {
                result.add(trimmed);
            }
        }
        return result;
    }

    public static String joinTags(List<String> values) {
        return values == null ? "" : String.join(",", values);
    }

    /** 规范化 URL 仅用于重复检测，不改变用户看到的原始链接。 */
    public static String normalizeUrl(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            java.net.URI uri = java.net.URI.create(raw.trim());
            String scheme = uri.getScheme() == null ? "https" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!"http".equals(scheme) && !"https".equals(scheme)) return null;
            String host = uri.getHost();
            if (host == null) return null;
            host = host.toLowerCase(Locale.ROOT);
            if (host.startsWith("www.")) host = host.substring(4);
            int port = uri.getPort();
            String path = uri.getPath() == null ? "" : uri.getPath();
            if (path.endsWith("/")) path = path.substring(0, path.length() - 1);
            String query = sanitizeQuery(uri.getQuery());
            StringBuilder sb = new StringBuilder(scheme).append("://").append(host);
            if (port > 0 && !((port == 80 && "http".equals(scheme)) || (port == 443 && "https".equals(scheme)))) {
                sb.append(':').append(port);
            }
            sb.append(path);
            if (!query.isEmpty()) sb.append('?').append(query);
            return sb.toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String sanitizeQuery(String query) {
        if (query == null || query.isBlank()) return "";
        List<String> kept = new ArrayList<>();
        for (String pair : query.split("&")) {
            String key = pair.contains("=") ? pair.substring(0, pair.indexOf('=')) : pair;
            String lower = key.toLowerCase(Locale.ROOT);
            if (lower.startsWith("utm_") || lower.equals("from") || lower.equals("spm") || lower.equals("share_source")) {
                continue;
            }
            kept.add(pair);
        }
        kept.sort(String::compareTo);
        return String.join("&", kept);
    }

    /** 赛事名称归一化：去除标点、空白和常见公告前后缀，保留年份与届次信息。 */
    public static String nameKey(String name) {
        if (name == null) return null;
        String value = name.toLowerCase(Locale.ROOT)
                .replaceAll("关于(?:举办|开展|组织|举行)", "")
                .replaceAll("(?:的)?(?:通知|公告|实施方案|工作方案|邀请函)$", "")
                .replaceAll("[\\s\\p{Punct}，。；：、（）()《》【】“”‘’·—－]", "");
        return value.isBlank() ? null : value;
    }

    public static int nameSimilarity(String a, String b) {
        String left = nameKey(a);
        String right = nameKey(b);
        if (left == null || right == null) return 0;
        if (left.equals(right)) return 100;
        Set<String> ga = bigrams(left);
        Set<String> gb = bigrams(right);
        if (ga.isEmpty() || gb.isEmpty()) return 0;
        Set<String> intersection = new LinkedHashSet<>(ga);
        intersection.retainAll(gb);
        Set<String> union = new LinkedHashSet<>(ga);
        union.addAll(gb);
        return (int) Math.round(intersection.size() * 100.0 / union.size());
    }

    private static Set<String> bigrams(String value) {
        Set<String> grams = new LinkedHashSet<>();
        if (value.length() == 1) {
            grams.add(value);
            return grams;
        }
        for (int i = 0; i + 1 < value.length(); i++) {
            grams.add(value.substring(i, i + 2));
        }
        return grams;
    }

    /** 在正文中定位包含关键词的第一句话，作为可核对证据。 */
    public static String sentenceWith(String text, String keyword) {
        if (text == null || keyword == null || keyword.isBlank()) return null;
        for (String sentence : text.split("(?<=[。！？；\\n])")) {
            if (sentence.contains(keyword)) {
                String trimmed = sentence.trim().replaceAll("\\s+", " ");
                if (!trimmed.isEmpty()) return trimmed.length() > 300 ? trimmed.substring(0, 300) : trimmed;
            }
        }
        return null;
    }
}
