package com.campus.competition.service;

import com.campus.competition.support.TextSignals;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 无模型时的公告规则提取。只认正文中带完整年月日的日期，不根据“今年/本届”推算；
 * 多个报名截止（校内/区域/全国/不同赛道）不静默择一，而是留空并写入冲突。
 */
public final class RuleCompetitionExtractor {

    private static final Pattern DATE = Pattern.compile(
            "(20\\d{2})\\s*(?:年|[-/.])\\s*(\\d{1,2})\\s*(?:月|[-/.])\\s*(\\d{1,2})\\s*日?"
                    + "(?:\\s*(\\d{1,2})\\s*[:：]\\s*(\\d{2}))?");
    /** 同年范围内的“5月20日”式结束日期，只在同一句已有完整年份日期时使用。 */
    private static final Pattern RANGE_END_MONTH_DAY = Pattern.compile(
            "(?:至|到|—|–|-|~|～|－)\\s*(?:(20\\d{2})\\s*年)?\\s*(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*日");
    private static final Pattern RANGE_END_DAY = Pattern.compile(
            "(?:至|到|—|–|-|~|～|－)\\s*(\\d{1,2})\\s*日");
    private static final Pattern MONTH_DAY_ONLY = Pattern.compile(
            "(?<!\\d)(?<!\\d年)(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*日");
    private static final Pattern ABOUT = Pattern.compile(
            "关于(?:举办|开展|组织|举行)(?:第?[一二三四五六七八九十百0-9]+届)?(.{4,60}?)(?:的通知|的公告|的函|工作方案|实施方案)");
    private static final Pattern ORGANIZER = Pattern.compile(
            "主办(?:单位|方)?[：:]\\s*([^\\n，。；]{2,50})");
    private static final Pattern EDITION = Pattern.compile("第[一二三四五六七八九十百0-9]+届");
    private static final Pattern LEVEL = Pattern.compile("(国际级|国家级|省级|校级|市级)");
    private static final Pattern TRACK = Pattern.compile(
            "(?:赛道|组别)[：:]\\s*([^\\n，。；]{2,30})|(本科组|高职组|研究生组|教师组|学生组|专业组|业余组)");
    private static final Pattern RANGE = Pattern.compile("^\\s*(?:至|到|—|–|-|~|～|－|起至)\\s*$");

    public CompetitionExtraction extract(String text, String pageTitle) {
        CompetitionExtraction result = new CompetitionExtraction();
        result.setMode("RULE");
        if (text == null || text.isBlank()) {
            result.getMissing().add("正文为空");
            return result;
        }
        String content = text.trim();
        String title = cleanTitle(pageTitle);

        String name = null;
        String nameEvidence = null;
        Matcher about = ABOUT.matcher(content);
        if (about.find()) {
            name = cleanName(about.group(1));
            nameEvidence = about.group(0);
        }
        if (name == null && title != null) {
            name = title;
            nameEvidence = title;
        }
        if (name == null) {
            String line = firstLineWith(content, "大赛", "竞赛", "比赛", "挑战赛", "锦标赛");
            if (line != null) {
                name = cleanName(line);
                nameEvidence = line;
            }
        }
        result.setName(limit(name, 200));
        if (nameEvidence != null) result.getEvidence().put("name", limit(nameEvidence, 300));

        Matcher organizer = ORGANIZER.matcher(content);
        if (organizer.find()) {
            String value = organizer.group(1)
                    .replaceAll("\\s*(承办|协办|支持|指导).*$", "").trim();
            if (!value.isEmpty()) {
                result.setOrganizer(limit(value, 200));
                result.getEvidence().put("organizer", limit(organizer.group(0), 300));
            }
        }

        Matcher edition = EDITION.matcher(content);
        if (edition.find()) {
            result.setEdition(edition.group());
            result.getEvidence().put("edition", TextSignals.sentenceWith(content, edition.group()));
        }
        Matcher level = LEVEL.matcher(content);
        if (level.find()) {
            result.setLevel(level.group(1));
            result.getEvidence().put("level", TextSignals.sentenceWith(content, level.group(1)));
        }
        Matcher track = TRACK.matcher(content);
        if (track.find()) {
            String value = track.group(1) != null ? track.group(1) : track.group(2);
            result.setTrack(limit(value, 100));
            result.getEvidence().put("track", TextSignals.sentenceWith(content, value));
        }

        String description = firstSentence(content, 20, 300);
        if (description != null) {
            result.setDescription(description);
            result.getEvidence().put("description", limit(description, 300));
        }
        String eligibility = sentenceWithAny(content,
                "参赛资格", "参赛对象", "参赛条件", "报名条件", "面向", "仅限", "凡");
        if (eligibility != null) {
            result.setEligibilityText(limit(eligibility, 500));
            result.getEvidence().put("eligibilityText", limit(eligibility, 300));
        }

        extractDates(content, result);

        Set<String> tags = new LinkedHashSet<>(TextSignals.capabilities(
                result.getName(), result.getDescription(), result.getEligibilityText()));
        result.setTags(new ArrayList<>(tags));
        if (!tags.isEmpty()) {
            result.getWarnings().add("方向标签由赛事名称/正文关键词推断，需管理员确认后才作为平台标签");
        }
        if (result.getYear() == null) {
            Integer explicitYear = explicitYear(result.getName());
            if (explicitYear != null) {
                result.setYear(explicitYear);
                result.getEvidence().put("year", TextSignals.sentenceWith(content, String.valueOf(explicitYear)));
            } else if (result.getSignupEnd() != null) {
                result.setYear(result.getSignupEnd().getYear());
                String dateEvidence = result.getEvidence().get("signupEnd");
                if (dateEvidence != null) result.getEvidence().put("year", dateEvidence);
            }
        }

        if (result.getName() == null) result.getMissing().add("赛事名称");
        if (result.getOrganizer() == null) result.getMissing().add("主办方");
        if (result.getSignupEnd() == null) result.getMissing().add("报名截止时间");
        if (result.getEligibilityText() == null) result.getMissing().add("参赛条件（原文未提及，需人工补充）");
        if (result.getSignupStart() == null) result.getWarnings().add("未提取到报名开始时间，可留空后在展示中说明");
        if (result.getStartTime() == null) result.getWarnings().add("未提取到开赛时间，可留空");
        return result;
    }

    private void extractDates(String content, CompetitionExtraction result) {
        List<DateCandidate> signupStarts = new ArrayList<>();
        List<DateCandidate> signupEnds = new ArrayList<>();
        List<DateCandidate> eventStarts = new ArrayList<>();
        boolean monthDaySkipped = false;

        for (String sentence : content.split("(?<=[。！？；\\n])")) {
            String line = sentence.trim();
            if (line.isEmpty()) continue;
            List<Match> matches = new ArrayList<>();
            Matcher matcher = DATE.matcher(line);
            while (matcher.find()) {
                LocalDateTime value = parse(matcher);
                if (value != null) {
                    matches.add(new Match(value, matcher.group(4) == null,
                            matcher.start(), matcher.end(), limit(line, 300)));
                }
            }
            if (matches.isEmpty()) {
                if (!monthDaySkipped && MONTH_DAY_ONLY.matcher(line).find()
                        && (line.contains("报名") || line.contains("截止"))) {
                    result.getConflicts().add("正文存在只有月日的日期（" + limit(line, 80)
                            + "），未自动推算年份，请人工确认");
                    monthDaySkipped = true;
                }
                continue;
            }
            boolean signup = line.contains("报名") || line.contains("申报") || line.contains("提交材料");
            boolean event = line.contains("比赛") || line.contains("竞赛") || line.contains("开赛")
                    || line.contains("决赛") || line.contains("举办") || line.contains("赛程");
            String label = stageLabel(line);

            if (matches.size() >= 2 && isRange(line, matches.get(0), matches.get(1))) {
                Match first = matches.get(0);
                Match second = matches.get(1);
                if (signup || !event) {
                    signupStarts.add(new DateCandidate(first.value(), first.dateOnly(), first.evidence(), label));
                    signupEnds.add(new DateCandidate(second.value(), second.dateOnly(), second.evidence(), label));
                } else {
                    eventStarts.add(new DateCandidate(first.value(), first.dateOnly(), first.evidence(), label));
                }
                continue;
            }
            Match single = matches.get(0);
            // “2026年5月1日至5月20日”：结束日期省略年份，但同一句已有明确年份，可安全归属。
            if (signup && matches.size() == 1) {
                LocalDateTime rangeEnd = rangeEndInSameSentence(line, single);
                if (rangeEnd != null) {
                    signupStarts.add(new DateCandidate(single.value(), single.dateOnly(), single.evidence(), label));
                    signupEnds.add(new DateCandidate(rangeEnd, true, single.evidence(), label));
                    continue;
                }
            }
            if (signup) {
                if (line.contains("截止") || line.contains("截至") || line.contains("报名结束")) {
                    signupEnds.add(new DateCandidate(single.value(), single.dateOnly(), single.evidence(), label));
                } else {
                    signupStarts.add(new DateCandidate(single.value(), single.dateOnly(), single.evidence(), label));
                }
            } else if (event) {
                eventStarts.add(new DateCandidate(single.value(), single.dateOnly(), single.evidence(), label));
            }
        }

        DateCandidate start = choose(signupStarts, "报名开始", result);
        DateCandidate end = choose(signupEnds, "报名截止", result);
        DateCandidate event = choose(eventStarts, "开赛时间", result);

        if (start != null) {
            result.setSignupStart(start.dateOnly ? start.value.toLocalDate().atStartOfDay() : start.value);
            result.getEvidence().put("signupStart", start.evidence);
        }
        if (end != null) {
            // 只有日期时按北京时间当日 23:59:59 暂存，避免被误判为当日 00:00 已截止；
            // 发布前必须由管理员确认这一解释。
            result.setSignupEnd(end.dateOnly ? end.value.toLocalDate().atTime(23, 59, 59) : end.value);
            result.setSignupEndPrecision(end.dateOnly ? "DATE" : "DATETIME");
            result.getEvidence().put("signupEnd", end.evidence);
            if (end.dateOnly) {
                result.setDateNote("公告只给出日期，已按北京时间当日 23:59:59 暂存，需管理员确认当日截止的解释");
            }
        }
        if (event != null) {
            result.setStartTime(event.dateOnly ? event.value.toLocalDate().atStartOfDay() : event.value);
            result.getEvidence().put("startTime", event.evidence);
            if (event.dateOnly && result.getDateNote() == null) {
                result.setDateNote("开赛时间只给出日期，已按当日 00:00 暂存，展示时只显示日期");
            }
        }
        if (result.getSignupStart() != null && result.getSignupEnd() != null
                && result.getSignupStart().isAfter(result.getSignupEnd())) {
            result.getConflicts().add("报名开始时间晚于报名截止时间，需人工核对原文");
        }
        if (result.getStartTime() != null && result.getSignupEnd() != null
                && result.getStartTime().isBefore(result.getSignupEnd())) {
            result.getConflicts().add("开赛时间早于报名截止时间，可能是多阶段赛事或日期归属不同，需人工核对");
        }
        if (result.getSignupEndPrecision() == null) {
            result.setSignupEndPrecision("UNKNOWN");
        }
    }

    /** 同一字段出现多个不同日期时不择一，留空并列出证据。 */
    private DateCandidate choose(List<DateCandidate> candidates, String label, CompetitionExtraction result) {
        if (candidates.isEmpty()) return null;
        Map<String, DateCandidate> unique = new LinkedHashMap<>();
        for (DateCandidate candidate : candidates) {
            unique.putIfAbsent(candidate.value().toString(), candidate);
        }
        if (unique.size() == 1) return unique.values().iterator().next();
        List<String> parts = new ArrayList<>();
        for (DateCandidate candidate : unique.values()) {
            String stage = candidate.label() == null ? "" : "[" + candidate.label() + "] ";
            parts.add(stage + candidate.value().toLocalDate() + "（" + limit(candidate.evidence(), 60) + "）");
        }
        result.getConflicts().add("发现多个" + label + "：" + String.join("；", parts) + "。请人工确认适用阶段");
        return null;
    }

    private LocalDateTime rangeEndInSameSentence(String line, Match single) {
        String tail = line.substring(Math.min(single.end(), line.length()));
        Matcher monthDay = RANGE_END_MONTH_DAY.matcher(tail);
        if (monthDay.find()) {
            try {
                int year = monthDay.group(1) != null ? Integer.parseInt(monthDay.group(1)) : single.value().getYear();
                int month = Integer.parseInt(monthDay.group(2));
                int day = Integer.parseInt(monthDay.group(3));
                if (year < 2000 || year > 2100) return null;
                return LocalDateTime.of(year, month, day, 0, 0);
            } catch (Exception ignored) {
                return null;
            }
        }
        Matcher dayOnly = RANGE_END_DAY.matcher(tail);
        if (dayOnly.find()) {
            try {
                int day = Integer.parseInt(dayOnly.group(1));
                // 只省略“日”时，不跨月推断；跨月必须由原文给出月份。
                if (day < single.value().getDayOfMonth()) return null;
                LocalDate date = single.value().toLocalDate().withDayOfMonth(day);
                return date.atStartOfDay();
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private boolean isRange(String line, Match first, Match second) {
        if (second.start <= first.end) return false;
        String between = line.substring(first.end, second.start).trim();
        return RANGE.matcher(between).matches()
                || between.contains("至") || between.contains("到") || between.contains("~")
                || between.contains("～") || between.contains("—") || between.contains("－");
    }

    private LocalDateTime parse(Matcher matcher) {
        try {
            int year = Integer.parseInt(matcher.group(1));
            int month = Integer.parseInt(matcher.group(2));
            int day = Integer.parseInt(matcher.group(3));
            int hour = matcher.group(4) == null ? 0 : Integer.parseInt(matcher.group(4));
            int minute = matcher.group(5) == null ? 0 : Integer.parseInt(matcher.group(5));
            if (year < 2000 || year > 2100) return null;
            return LocalDateTime.of(year, month, day, hour, minute);
        } catch (Exception e) {
            return null;
        }
    }

    private String stageLabel(String line) {
        for (String stage : List.of("校内", "校级", "区域", "省赛", "省级", "全国", "国赛", "决赛", "初赛", "复赛")) {
            if (line.contains(stage)) return stage;
        }
        return null;
    }

    private String cleanTitle(String pageTitle) {
        if (pageTitle == null || pageTitle.isBlank()) return null;
        String value = pageTitle.trim();
        for (String separator : List.of("_", "|", "｜", " - ", "—")) {
            int index = value.indexOf(separator);
            if (index > 3) {
                value = value.substring(0, index).trim();
                break;
            }
        }
        value = value.replaceAll("^(通知公告|新闻中心|首页|教务通知)[\\s:：-]*", "").trim();
        if (value.length() < 4 || value.matches("(通知公告|新闻中心|首页)")) return null;
        return cleanName(value);
    }

    private String cleanName(String value) {
        if (value == null) return null;
        String result = value.replaceAll("^[《【\\[]|[》】\\]]$", "")
                .replaceAll("\\s+", " ").trim();
        return result.length() < 4 ? null : result;
    }

    private String firstLineWith(String content, String... keywords) {
        for (String line : content.split("\\n")) {
            String trimmed = line.trim();
            if (trimmed.length() < 6 || trimmed.length() > 100) continue;
            for (String keyword : keywords) {
                if (trimmed.contains(keyword)) return trimmed;
            }
        }
        return null;
    }

    private String firstSentence(String content, int min, int max) {
        for (String sentence : content.split("(?<=[。！？\\n])")) {
            String trimmed = sentence.trim();
            if (trimmed.length() < min) continue;
            if (trimmed.contains("大赛") || trimmed.contains("竞赛") || trimmed.contains("比赛")
                    || trimmed.contains("挑战赛")) {
                return limit(trimmed, max);
            }
        }
        String first = content.trim();
        return first.length() < min ? null : limit(first, max);
    }

    private String sentenceWithAny(String content, String... keywords) {
        for (String sentence : content.split("(?<=[。！？；\\n])")) {
            String trimmed = sentence.trim();
            for (String keyword : keywords) {
                if (trimmed.contains(keyword) && trimmed.length() >= 6) {
                    return limit(trimmed, 500);
                }
            }
        }
        return null;
    }

    private Integer explicitYear(String name) {
        if (name == null) return null;
        Matcher matcher = Pattern.compile("(20\\d{2})\\s*年?").matcher(name);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : null;
    }

    private String limit(String value, int max) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private record Match(LocalDateTime value, boolean dateOnly, int start, int end, String evidence) {
    }

    private record DateCandidate(LocalDateTime value, boolean dateOnly, String evidence, String label) {
    }
}
