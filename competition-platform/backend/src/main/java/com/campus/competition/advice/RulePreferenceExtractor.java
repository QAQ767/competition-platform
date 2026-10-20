package com.campus.competition.advice;

import com.campus.competition.support.TextSignals;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 无模型时的保守规则提取器：只识别用户明确写出的数字、方向词、否定句和“缺某角色”，
 * 不推断经历、资格或获奖情况。规则结果会标注为规则模式，前端不会显示成 AI 分析。
 */
public final class RulePreferenceExtractor {

    private static final Pattern HOURS = Pattern.compile(
            "(?:每(?:周|星期)|周均)[^，。；;\\n]{0,12}?(\\d{1,2})\\s*(?:个)?\\s*小时"
                    + "|(?:只能|最多|大概|大约|可以)?投入[^，。；;\\n]{0,8}?(\\d{1,2})\\s*(?:个)?\\s*小时"
                    + "|(\\d{1,2})\\s*小时\\s*/\\s*(?:周|星期)");
    private static final Pattern SKILL = Pattern.compile(
            "(?:会|熟悉|掌握|用过|使用|了解)\\s*([A-Za-z][A-Za-z0-9+#.\\-]{0,14}|[\\u4e00-\\u9fa5]{2,10})");
    private static final Pattern MISSING_ROLE = Pattern.compile(
            "缺(?:少)?([^，。；;\\n]{1,12}?)的?(?:队伍|团队|队友|队)");
    private static final Pattern WANT_ROLE = Pattern.compile(
            "(?:想|希望|打算|愿意)(?:做|负责|当|担任|找)?([^，。；;\\n]{1,10}?)(?:方向|角色|的活|的工作|$|[，。；;])");
    private static final Pattern WINDOW = Pattern.compile(
            "(?:未来|近|最近|接下来)\\s*(\\d{1,3})\\s*天|(\\d{1,3})\\s*天内");
    private static final Pattern DIFFICULTY_EASY = Pattern.compile("入门|零基础|简单|练手|第一次参加");
    private static final Pattern DIFFICULTY_HARD = Pattern.compile("进阶|高难度|冲国奖|国赛|省赛|有挑战");
    private static final Pattern NEGATION = Pattern.compile(
            "不参加|不想|不考虑|不报|排除|不要|别参加|拒绝|不感兴趣|不做");
    private static final Pattern TEAM_ONLY = Pattern.compile("只找队伍|只想加队伍|只考虑队伍|找队伍|加队伍");

    public RuleExtraction extract(String message) {
        RuleExtraction result = new RuleExtraction();
        if (message == null || message.isBlank()) {
            result.reply = "请用一句话说明你的技能、时间或想参加的方向。";
            return result;
        }
        String text = message.trim();
        String[] clauses = text.split("[，。；;！!？?\\n]");

        // 1. 每周时间：多个数字时取最后一个明确表述，避免把“做过 3 个项目”误当时间。
        Matcher hours = HOURS.matcher(text);
        Integer weekly = null;
        while (hours.find()) {
            String value = hours.group(1) != null ? hours.group(1)
                    : hours.group(2) != null ? hours.group(2) : hours.group(3);
            try {
                int parsed = Integer.parseInt(value);
                if (parsed > 0 && parsed <= 80) weekly = parsed;
            } catch (NumberFormatException ignored) {
                // 忽略无法解析的数字
            }
        }
        if (weekly != null) {
            result.changes.add(new PreferenceChange("weeklyHours", "SET", List.of(String.valueOf(weekly))));
        } else if (text.matches("(?s).*0\\s*小时.*")) {
            result.questions.add("你写的是 0 小时，是暂未确定，还是完全没有时间？");
        }

        // 2. 技能与角色、排除项。逐句判断否定语境。
        List<String> skills = new ArrayList<>();
        List<String> roles = new ArrayList<>();
        List<String> directions = new ArrayList<>();
        List<String> excluded = new ArrayList<>();
        for (String clause : clauses) {
            String clean = clause.trim();
            if (clean.isEmpty()) continue;
            boolean negative = NEGATION.matcher(clean).find();
            Set<String> mentioned = TextSignals.capabilities(clean);

            if (negative) {
                for (String direction : mentioned) {
                    if (!excluded.contains(direction)) excluded.add(direction);
                }
                continue;
            }

            Matcher skill = SKILL.matcher(clean);
            while (skill.find()) {
                String raw = skill.group(1).trim();
                if (raw.length() < 2 || isStopWord(raw)) continue;
                Set<String> mapped = TextSignals.capabilities(raw);
                if (!mapped.isEmpty()) {
                    for (String direction : mapped) {
                        if (!skills.contains(direction)) skills.add(direction);
                    }
                } else if (!skills.contains(raw)) {
                    skills.add(raw);
                }
            }

            Matcher missing = MISSING_ROLE.matcher(clean);
            if (missing.find()) {
                for (String direction : TextSignals.capabilities(missing.group(1))) {
                    if (!roles.contains(direction)) roles.add(direction);
                }
                result.teamOnly = true;
            }

            Matcher want = WANT_ROLE.matcher(clean);
            if (want.find()) {
                for (String direction : TextSignals.capabilities(want.group(1))) {
                    if (!roles.contains(direction)) roles.add(direction);
                }
            }

            // 只在出现“参加/方向/兴趣/比赛/竞赛”等意图词时，把方向词记入兴趣。
            if (clean.matches("(?s).*(?:参加|方向|兴趣|想做|偏好|比赛|竞赛|赛事).*")) {
                for (String direction : mentioned) {
                    if (!directions.contains(direction)) directions.add(direction);
                }
            }
        }
        if (!skills.isEmpty()) result.changes.add(new PreferenceChange("skills", "ADD", skills));
        if (!roles.isEmpty()) result.changes.add(new PreferenceChange("roles", "ADD", roles));
        if (!directions.isEmpty()) result.changes.add(new PreferenceChange("directions", "ADD", directions));
        if (!excluded.isEmpty()) {
            result.changes.add(new PreferenceChange("excludedKeywords", "ADD", excluded));
            result.changes.add(new PreferenceChange("directions", "REMOVE", excluded));
        }

        Matcher window = WINDOW.matcher(text);
        if (window.find()) {
            String value = window.group(1) != null ? window.group(1) : window.group(2);
            try {
                int days = Integer.parseInt(value);
                if (days >= 7 && days <= 365) {
                    result.changes.add(new PreferenceChange("windowDays", "SET", List.of(String.valueOf(days))));
                }
            } catch (NumberFormatException ignored) {
                // 忽略
            }
        }

        if (DIFFICULTY_EASY.matcher(text).find()) {
            result.changes.add(new PreferenceChange("difficulty", "SET", List.of("入门")));
        } else if (DIFFICULTY_HARD.matcher(text).find()) {
            result.changes.add(new PreferenceChange("difficulty", "SET", List.of("进阶")));
        }
        if (TEAM_ONLY.matcher(text).find()) {
            result.changes.add(new PreferenceChange("teamOnly", "SET", List.of("true")));
        }

        result.questions.addAll(defaultQuestions(result.changes));
        result.reply = buildReply(result.changes, result.questions);
        return result;
    }

    private List<String> defaultQuestions(List<PreferenceChange> changes) {
        List<String> questions = new ArrayList<>();
        boolean hasHours = changes.stream().anyMatch(c -> "weeklyHours".equals(c.getField()));
        boolean hasDirection = changes.stream().anyMatch(c ->
                "directions".equals(c.getField()) || "roles".equals(c.getField()) || "skills".equals(c.getField()));
        if (!hasHours) questions.add("每周大概能投入多少小时？");
        if (!hasDirection) questions.add("你更想做哪类工作（前端、后端、算法、建模、文档等）？");
        return questions.size() > 2 ? questions.subList(0, 2) : questions;
    }

    private String buildReply(List<PreferenceChange> changes, List<String> questions) {
        if (changes.isEmpty()) {
            if (!questions.isEmpty()) {
                return "我还没有从这句话里提取到明确的需求。可以补充时间、技能或想参加的方向。";
            }
            return "这句话没有产生新的需求变更。";
        }
        List<String> parts = new ArrayList<>();
        for (PreferenceChange change : changes) {
            parts.add(change.display());
        }
        return "已按你的原话记录：" + String.join("；", parts) + "。本次未调用模型，使用规则提取。";
    }

    private boolean isStopWord(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.equals("一些") || lower.equals("一点") || lower.equals("什么")
                || lower.equals("这个") || lower.equals("那个") || lower.equals("东西");
    }

    /** 规则提取结果。changes 只包含明确识别到的字段。 */
    public static final class RuleExtraction {
        public String reply = "";
        public final List<PreferenceChange> changes = new ArrayList<>();
        public final List<String> questions = new ArrayList<>();
        public boolean teamOnly;
    }
}
