package com.campus.competition.service;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 最小 HTML 正文提取：去脚本/样式/标签、解码常见实体、保留段落换行。 */
public final class HtmlTextExtractor {

    private static final Pattern TITLE = Pattern.compile("<title[^>]*>(.*?)</title>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    private static final Pattern SCRIPT = Pattern.compile(
            "<(script|style|noscript|svg|canvas|iframe)[^>]*>.*?</\\1>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern BLOCK = Pattern.compile(
            "</?(p|div|section|article|header|footer|li|tr|h[1-6]|table|ul|ol|dl|dt|dd|blockquote)[^>]*>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BR = Pattern.compile("<br\\s*/?>", Pattern.CASE_INSENSITIVE);
    private static final Pattern TAG = Pattern.compile("<[^>]+>", Pattern.DOTALL);
    private static final Pattern NUMERIC = Pattern.compile("&#(x?[0-9a-fA-F]+);");

    private HtmlTextExtractor() {
    }

    public static String title(String html) {
        if (html == null) return null;
        Matcher matcher = TITLE.matcher(html);
        if (!matcher.find()) return null;
        String value = cleanInline(matcher.group(1));
        return value.isBlank() ? null : value;
    }

    public static String toText(String html) {
        if (html == null) return "";
        String value = COMMENT.matcher(html).replaceAll("");
        value = SCRIPT.matcher(value).replaceAll("\n");
        value = BR.matcher(value).replaceAll("\n");
        value = BLOCK.matcher(value).replaceAll("\n");
        value = TAG.matcher(value).replaceAll(" ");
        value = decode(value);
        value = value.replace('\u00a0', ' ').replace("\r\n", "\n").replace('\r', '\n');
        value = value.replaceAll("[\\t\\f\\x0B]+", " ");
        value = value.replaceAll(" *\\n *", "\n");
        value = value.replaceAll(" {2,}", " ");
        value = value.replaceAll("\\n{3,}", "\n\n");
        return value.trim();
    }

    public static String cleanInline(String value) {
        if (value == null) return "";
        return decode(TAG.matcher(value).replaceAll(" "))
                .replace('\u00a0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String decode(String value) {
        String result = value
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&ldquo;", "“")
                .replace("&rdquo;", "”")
                .replace("&mdash;", "—")
                .replace("&ndash;", "–")
                .replace("&hellip;", "…");
        Matcher matcher = NUMERIC.matcher(result);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String raw = matcher.group(1);
            try {
                int code = raw.toLowerCase(Locale.ROOT).startsWith("x")
                        ? Integer.parseInt(raw.substring(1), 16)
                        : Integer.parseInt(raw);
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(
                        new String(Character.toChars(code))));
            } catch (Exception ignored) {
                matcher.appendReplacement(buffer, "");
            }
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }
}
