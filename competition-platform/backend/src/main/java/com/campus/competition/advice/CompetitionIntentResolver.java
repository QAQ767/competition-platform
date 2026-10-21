package com.campus.competition.advice;

import com.campus.competition.entity.Competition;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** 只把用户明确指向的赛事解析到平台身份。未找到或简称有歧义时保留约束，绝不放宽。 */
public final class CompetitionIntentResolver {
    private CompetitionIntentResolver() { }

    private static final Pattern INTENT = Pattern.compile(
            "(?:想参加|要参加|参加|关注|想找|寻找|找|推荐)\\s*([^，。；;！!？?\\n]{2,80})");
    private static final Pattern TITLE = Pattern.compile("[《“\"]([^》”\"]{2,80})[》”\"]");
    private static final Pattern NAMED = Pattern.compile("(?:中国|全国|国际)[\\p{IsHan}A-Za-z0-9]{2,60}?(?:大赛|竞赛)|[\\p{IsHan}A-Za-z0-9]{2,60}(?:大赛|挑战赛|锦标赛|邀请赛|杯)");
    // 条件语法不是赛事专名证据。尤其不能将“推荐”后剩余的任意文本当成硬约束。
    private static final Pattern CONDITION = Pattern.compile(
            "适合|适宜|面向|每周|小时|(?:相关|方向|领域|方面|类|类型)的?(?:比赛|竞赛|赛事|大赛|挑战赛)$"
                    + "|的(?:比赛|竞赛|赛事|大赛|挑战赛)$"
                    + "|^(?:[一二两三四五六七八九十几数0-9]+(?:个|场|项)|若干|一些|入门|新手|零基础|最近|未来|更多|其他|所有|全部)"
                    + "|^(?:比赛|竞赛|赛事|队伍|团队|相关队伍|这|那)$");

    public static List<String> resolve(String message, AdvicePreferences current,
                                       AdvicePreferences next, List<Competition> catalog) {
        if (catalog == null) catalog = List.of();
        if (message.matches("(?s).*(?:不限赛事|不限竞赛|所有赛事|全部赛事|取消赛事限制|取消竞赛限制).*")) {
            next.setCompetitionKeywords(List.of());
            next.setResolvedCompetitionIds(List.of());
            next.setCompetitionIntentStatus("NONE");
            return List.of();
        }

        Set<String> requests = new LinkedHashSet<>();
        boolean reopening = message.matches("(?s).*(?:也可以|可以重新|重新考虑|不再排除|取消排除|取消对|撤销排除|允许参加).*" );
        if (!reopening) {
            var quotes = TITLE.matcher(message);
            while (quotes.find()) {
                String title = quotes.group(1).trim();
                // 引号也可用于强调方向（如“算法”类比赛），此时不是在引用赛事名。
                if (!CONDITION.matcher(title).find() && !message.substring(quotes.end())
                        .matches("^\\s*(?:类|相关|方向|领域|方面|类型).*")) requests.add(title);
            }
            var intent = INTENT.matcher(message);
            while (intent.find()) {
                String value = cleanRequest(intent.group(1));
                for (String item : value.split("(?:和|或|、|以及)"))
                    if (hasIdentityEvidence(item.trim(), catalog)) requests.add(item.trim());
            }
            // 无“参加”动词时也识别平台完整名称或“某某杯”族名；较长名字优先，避免子串抢占身份。
            List<String> names = new ArrayList<>();
            for (Competition c : catalog) {
                if (c.getName() == null) continue;
                names.add(c.getName());
                String family = cupFamily(c.getName());
                if (family != null) names.add(family);
            }
            names.sort(Comparator.comparingInt(String::length).reversed());
            if (requests.isEmpty()) {
                for (String name : names) if (message.contains(name)
                        && requests.stream().noneMatch(existing -> existing.contains(name))
                        && !message.matches("(?s).*(?:不要|不参加|排除)" + Pattern.quote(name) + ".*")) requests.add(name);
            }
            if (requests.isEmpty()) {
                var named = NAMED.matcher(message);
                while (named.find()) {
                    String value = named.group().replaceFirst("^(?:我想参加|想参加|我想|请|推荐|参加)", "");
                    if (hasIdentityEvidence(value, catalog) && !message.matches("(?s).*(?:不要|不参加|排除)" + Pattern.quote(value) + ".*")) requests.add(value);
                }
            }
            // 模型可补充语义理解，但不能借空列表清除原约束，也不能编造用户没提过的名字。
            if (requests.isEmpty() && !next.getCompetitionKeywords().equals(current.getCompetitionKeywords())) {
                for (String name : next.getCompetitionKeywords()) if (hasIdentityEvidence(name, catalog)
                        && normalize(message).contains(normalize(name))) requests.add(name);
            }
        }
        if (requests.isEmpty()) next.setCompetitionKeywords(new ArrayList<>(current.getCompetitionKeywords()));
        else next.setCompetitionKeywords(new ArrayList<>(requests));

        if (next.getCompetitionKeywords().isEmpty()) {
            next.setResolvedCompetitionIds(List.of());
            next.setCompetitionIntentStatus("NONE");
            return List.of();
        }
        Set<Long> resolved = new LinkedHashSet<>();
        List<String> questions = new ArrayList<>();
        String status = "RESOLVED";
        for (String request : next.getCompetitionKeywords()) {
            String query = normalize(request);
            List<Competition> matches = catalog.stream().filter(c -> c.getName() != null
                    && normalize(c.getName()).contains(query)).toList();
            boolean family = matches.stream().anyMatch(c -> query.equals(normalize(cupFamily(c.getName()))));
            boolean exact = matches.stream().anyMatch(c -> query.equals(normalize(c.getName())));
            if (matches.isEmpty()) {
                status = "UNRESOLVED";
                questions.add("平台暂未找到「" + request + "」。请确认赛事名称，或说“取消赛事限制”再看其他比赛。");
            } else if (matches.stream().map(c -> normalize(c.getName())).distinct().count() > 1 && !family && !exact) {
                if (!"UNRESOLVED".equals(status)) status = "AMBIGUOUS";
                questions.add("「" + request + "」可能指：" + String.join("、", matches.stream().map(Competition::getName).distinct().limit(3).toList()) + "。你想看哪一场？");
            } else {
                for (Competition c : matches) {
                    if (family || !exact || query.equals(normalize(c.getName()))) resolved.add(c.getId());
                }
            }
        }
        next.setCompetitionIntentStatus(status);
        next.setResolvedCompetitionIds(new ArrayList<>(resolved));
        return questions.stream().limit(2).toList();
    }

    private static String cleanRequest(String value) {
        return value.replaceFirst("(?:相关|有关|的队伍|队伍|团队|，|推荐).*$", "")
                .replaceFirst("(?:的比赛|的竞赛)$", "").replaceFirst("(?:了|吧|呀|啊)$", "").trim();
    }

    /** 接受目录里的完整名称/可辨识简称，或有赛事命名结构的专名；模型提议也走相同证据门槛。 */
    private static boolean hasIdentityEvidence(String value, List<Competition> catalog) {
        if (value.isBlank() || CONDITION.matcher(value).find()) return false;
        String query = normalize(value);
        if (query.length() < 2) return false;
        if (catalog.stream().anyMatch(c -> query.equals(normalize(c.getName()))
                || query.equals(normalize(cupFamily(c.getName()))))) return true;
        // 目录子串命中本身不够：“算法”“Java”等条件也可能出现在真实标题中。
        boolean recognizableShortName = value.matches(".*(?:大赛|挑战赛|锦标赛|邀请赛|竞赛)$")
                || value.matches("[A-Z][A-Z0-9-]{1,15}");
        if (recognizableShortName && catalog.stream().anyMatch(c -> c.getName() != null
                && normalize(c.getName()).contains(query))) return true;
        return NAMED.matcher(value).matches();
    }

    private static String cupFamily(String name) {
        if (name == null) return null;
        int index = name.indexOf('杯');
        return index < 1 ? null : name.substring(0, index + 1).replaceFirst("^\\d{4}(?:年)?", "");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[\\s《》“”\"（()）·]", "");
    }
}
