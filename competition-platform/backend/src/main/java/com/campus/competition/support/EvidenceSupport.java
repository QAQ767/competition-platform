package com.campus.competition.support;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 证据片段校验：片段“出现在正文里”只是必要条件，还必须“支持这个取值”。
 *
 * <p>这里是确定性规则，不调用模型：
 * 1. 日期字段要求片段里确实有同一天，并检查该日期所在分句的语义（报名截止 / 报名开始 / 开赛）；
 * 2. 正文只给月日、没有年份时，不认模型补出的年份；
 * 3. 同一条证据里出现多个不同的报名截止时，归属不明，不静默择一；
 * 4. 文本字段要求片段里出现字段值，否则不能标成“已核验”。</p>
 */
public final class EvidenceSupport {

    public enum Verdict {
        OK,
        NO_DATE,
        MISMATCH,
        MONTH_DAY_ONLY,
        WRONG_FIELD,
        AMBIGUOUS,
        /** 原文写明了时刻，与取值的时刻不一致。 */
        TIME_MISMATCH,
        /** 原文只给出日期，取值却带了没有依据的具体时刻。 */
        TIME_UNSUPPORTED,
        /** 原文出现时刻线索（点/时/冒号等），但无法可靠解析，必须人工核对。 */
        TIME_UNPARSED
    }

    public record DateCheck(Verdict verdict, String detail) {
        public boolean ok() {
            return verdict == Verdict.OK;
        }
    }

    private static final Pattern FULL_DATE = Pattern.compile(
            "(20\\d{2})\\s*[年\\-/.．]\\s*(\\d{1,2})\\s*[月\\-/.．]\\s*(\\d{1,2})\\s*日?");
    private static final Pattern MONTH_DAY = Pattern.compile(
            "(?<![0-9])(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*日");
    private static final String SEPARATORS = "。；;，,\n\r";

    private static final Pattern SIGNUP_WORD = Pattern.compile("报名|申报|注册|提交材料");
    private static final Pattern CUTOFF_WORD = Pattern.compile("截止|截至|结束|止");
    private static final Pattern SIGNUP_START_WORD = Pattern.compile("开始|启动|开放|即日|起");

    /**
     * 开赛/活动线索。故意不包含“比赛”“大赛”单独出现的词，避免把“大赛报名截止”判成开赛。
     */
    private static final Pattern EVENT_WORD = Pattern.compile(
            "开赛|决赛|初赛|复赛|半决赛|举办时间|举行时间|比赛时间|竞赛时间|赛程|报到|评审|答辩");

    private EvidenceSupport() {
    }

    /** 文本字段：片段必须真的包含字段值，不能只是正文任意一句。 */
    public static boolean supportsText(String snippet, String value) {
        if (snippet == null || value == null) return false;
        String needle = compact(value);
        if (needle.isEmpty()) return false;
        String haystack = compact(snippet);
        if (haystack.contains(needle)) return true;
        // 描述/参赛条件可能被模型截断或改写标点，允许用较长值的前 12 字做支撑判断。
        if (needle.length() > 12) {
            String head = needle.substring(0, 12);
            return haystack.contains(head);
        }
        return false;
    }

    private static final Pattern TRACK_HINT = Pattern.compile("赛道|组别|类别|方向|单元|通道|赛项|项目类别");
    private static final Pattern EDITION_HINT = Pattern.compile("第[一二三四五六七八九十\\d]{1,4}届|届次");

    /**
     * 很短的字段值（如赛道「全国」）容易只是长词的一部分。
     * 这时要求片段里同时出现该字段的语义提示，避免“片段出现”被当成“片段支撑”。
     */
    public static boolean needsFieldHint(String field, String value) {
        if (value == null) return false;
        int length = compact(value).length();
        return length <= 4 && ("track".equals(field) || "edition".equals(field));
    }

    public static boolean hasFieldHint(String snippet, String field) {
        if (snippet == null) return false;
        return switch (field) {
            case "track" -> TRACK_HINT.matcher(snippet).find();
            case "edition" -> EDITION_HINT.matcher(snippet).find();
            default -> true;
        };
    }

    /** 日期字段：片段里的日期必须与取值同一天、同一时刻（若原文写明时刻），且分句语义对得上。 */
    public static DateCheck checkDate(String snippet, LocalDateTime value, String field) {
        return checkDate(snippet, value, field, false);
    }

    /**
     * @param dateOnlyPrecision 模型声明该字段只有日期精度（如 signupEndPrecision=DATE）。
     *                          原文也没有时刻时，约定的当日末/零点时刻不算“无依据补造”。
     */
    public static DateCheck checkDate(String snippet, LocalDateTime value, String field,
                                      boolean dateOnlyPrecision) {
        if (snippet == null || snippet.isBlank() || value == null) {
            return new DateCheck(Verdict.NO_DATE, "证据片段为空");
        }
        List<FoundDate> found = dates(snippet);
        if (found.isEmpty()) {
            return new DateCheck(Verdict.NO_DATE, "证据片段不含任何日期");
        }

        List<FoundDate> exact = found.stream()
                .filter(date -> date.year == value.getYear()
                        && date.month == value.getMonthValue()
                        && date.day == value.getDayOfMonth())
                .toList();
        if (!exact.isEmpty()) {
            FoundDate hit = exact.get(0);
            boolean rangeEnd = precededByRange(hit, snippet);
            // 区间写法：“2026年11月1日至12月20日”里，前一个是开始、后一个是结束。
            if ("signupStart".equals(field) && rangeEnd) {
                return new DateCheck(Verdict.WRONG_FIELD, "该日期是报名区间的结束日期，不是报名开始时间");
            }
            if ("signupEnd".equals(field) && followedByRange(hit, snippet)) {
                return new DateCheck(Verdict.WRONG_FIELD, "该日期是报名区间的开始日期，不是报名截止时间");
            }
            String context = contextText(snippet, hit.start);
            Verdict semantic = semantics(context, field, rangeEnd);
            if (semantic != Verdict.OK) {
                return new DateCheck(semantic, semantic == Verdict.WRONG_FIELD
                        ? "该日期在原文中不属于" + label(field) + "语义（如属于开赛/报名开始），不能作为其取值"
                        : "该日期在原文中未明确对应" + label(field));
            }
            DateCheck time = checkTime(hit, value, field, dateOnlyPrecision);
            if (!time.ok()) {
                return time;
            }
            if (multipleCutoffs(snippet, found)) {
                return new DateCheck(Verdict.AMBIGUOUS,
                        "同一条证据里出现多个不同的报名截止日期，归属不明");
            }
            return new DateCheck(Verdict.OK, null);
        }

        List<FoundDate> monthDayOnly = found.stream()
                .filter(date -> date.year < 0
                        && date.month == value.getMonthValue()
                        && date.day == value.getDayOfMonth())
                .toList();
        if (!monthDayOnly.isEmpty()) {
            return new DateCheck(Verdict.MONTH_DAY_ONLY,
                    "原文只给出 " + monthDayOnly.get(0).month + " 月 " + monthDayOnly.get(0).day
                            + " 日，未出现年份，不能确认是 " + value.toLocalDate());
        }

        return new DateCheck(Verdict.MISMATCH,
                "证据片段中的日期是 " + render(found.get(0)) + "，与取值 "
                        + value.toLocalDate() + " 不一致");
    }

    private static final String RANGE_CONNECTOR = "至到-—~～－";

    /** 日期前面紧跟“至/到/-”等连接词，说明它是区间的结束。 */
    private static boolean precededByRange(FoundDate date, String snippet) {
        for (int i = date.start - 1; i >= 0 && i >= date.start - 3; i--) {
            char c = snippet.charAt(i);
            if (Character.isWhitespace(c)) continue;
            return RANGE_CONNECTOR.indexOf(c) >= 0;
        }
        return false;
    }

    /** 日期后面紧跟“至/到/-”等连接词，说明它是区间的开始。 */
    private static boolean followedByRange(FoundDate date, String snippet) {
        for (int i = date.end; i < snippet.length() && i < date.end + 3; i++) {
            char c = snippet.charAt(i);
            if (Character.isWhitespace(c)) continue;
            return RANGE_CONNECTOR.indexOf(c) >= 0;
        }
        return false;
    }

    private static boolean multipleCutoffs(String snippet, List<FoundDate> found) {        long distinct = found.stream()
                .filter(date -> CUTOFF_WORD.matcher(contextText(snippet, date.start)).find())
                .map(date -> date.year + "/" + date.month + "/" + date.day)
                .distinct()
                .count();
        return distinct > 1;
    }

    private static Verdict semantics(String context, String field, boolean rangeEnd) {
        boolean signup = SIGNUP_WORD.matcher(context).find() || CUTOFF_WORD.matcher(context).find();
        boolean cutoff = CUTOFF_WORD.matcher(context).find();
        boolean start = SIGNUP_START_WORD.matcher(context).find();
        boolean event = EVENT_WORD.matcher(context).find();
        return switch (field) {
            case "signupEnd" -> {
                // 不能只凭“报名”一词认定截止：显式“开始/启动/开放/即日起”且无截止依据时是开始语义。
                if (cutoff) yield Verdict.OK;
                if (rangeEnd && signup) yield Verdict.OK; // “报名：即日起至X”里 X 是区间终点
                if (signup && start) yield Verdict.WRONG_FIELD;
                if (signup) yield Verdict.OK;
                yield event ? Verdict.WRONG_FIELD : Verdict.AMBIGUOUS;
            }
            case "signupStart" -> {
                if (signup && cutoff && !start) yield Verdict.WRONG_FIELD;
                if (signup) yield Verdict.OK;
                yield event ? Verdict.WRONG_FIELD : Verdict.AMBIGUOUS;
            }
            case "startTime" -> signup ? Verdict.WRONG_FIELD : event ? Verdict.OK : Verdict.AMBIGUOUS;
            default -> Verdict.OK;
        };
    }

    /**
     * 时刻与精度核对：原文写明时刻时必须与取值一致（原文精度优先，不能用 DATE 声明抹掉时刻）；
     * 原文只有日期时，取值不得带有无依据的具体时刻（约定的零点/当日末除外）。
     */
    private static DateCheck checkTime(FoundDate hit, LocalDateTime value, String field,
                                       boolean dateOnlyPrecision) {
        if (hit.timeHinted) {
            return new DateCheck(Verdict.TIME_UNPARSED,
                    "原文出现了时刻线索但无法可靠解析，请人工核对后确认具体截止时刻");
        }
        if (hit.hour >= 0) {
            if (value.getHour() != hit.hour || value.getMinute() != hit.minute) {
                return new DateCheck(Verdict.TIME_MISMATCH,
                        "原文写明的时刻是 " + String.format("%02d:%02d", hit.hour, hit.minute)
                                + "，与取值时刻 " + String.format("%02d:%02d", value.getHour(), value.getMinute())
                                + " 不一致");
            }
            return new DateCheck(Verdict.OK, null);
        }
        // 原文确实只给了日期：截止字段只有“DATE + 当日末”是约定转换，其余时刻（含 00:00）都是无依据补造。
        if ("signupEnd".equals(field)) {
            boolean conventionalEnd = dateOnlyPrecision
                    && value.getHour() == 23 && value.getMinute() == 59;
            if (conventionalEnd) return new DateCheck(Verdict.OK, null);
            return new DateCheck(Verdict.TIME_UNSUPPORTED,
                    "原文只给出日期、未给出时刻，不能确认取值中的具体时刻 "
                            + String.format("%02d:%02d", value.getHour(), value.getMinute()));
        }
        // 非截止字段：零点可视为自然日边界，其余具体时刻仍属无依据补造。
        boolean midnight = value.getHour() == 0 && value.getMinute() == 0 && value.getSecond() == 0;
        if (midnight) return new DateCheck(Verdict.OK, null);
        return new DateCheck(Verdict.TIME_UNSUPPORTED,
                "原文只给出日期、未给出时刻，不能确认取值中的具体时刻 "
                        + String.format("%02d:%02d", value.getHour(), value.getMinute()));
    }

    private static String label(String field) {
        return switch (field) {
            case "signupEnd" -> "报名截止时间";
            case "signupStart" -> "报名开始时间";
            case "startTime" -> "开赛时间";
            default -> "该字段";
        };
    }

    private static String render(FoundDate date) {
        return date.year < 0
                ? date.month + " 月 " + date.day + " 日"
                : date.year + "-" + String.format("%02d", date.month) + "-" + String.format("%02d", date.day);
    }

    private record FoundDate(int year, int month, int day, int hour, int minute,
                             boolean timeHinted, int start, int end) {
    }

    /**
     * 日期后紧跟的时刻：支持“12时00分”“12:00”“12点”“12点半”“12点15分”“下午3点”等写法。
     * 修饰词只处理上午/早上/凌晨/下午/晚上/中午，其他情况无法可靠解析就不取。
     */
    private static final Pattern TIME_TOKEN = Pattern.compile(
            "(上午|早上|凌晨|下午|晚上|中午)?(\\d{1,2})"
                    + "(?:[:：](\\d{1,2})|时(\\d{1,2})?分?|点(半|(\\d{1,2})分)?)");

    /** 出现时刻线索（点/时/冒号 + 数字，含中文数字）但无法完整解析。 */
    private static final Pattern TIME_HINT = Pattern.compile(
            "(上午|早上|凌晨|下午|晚上|中午)?[0-9一二两三四五六七八九十]{1,3}\\s*(点|时|[:：])");

    /** 时刻解析失败但确有线索的哨兵。 */
    private static final int[] TIME_UNPARSED = new int[]{-2, -2};

    private static int[] trailingTime(String text, int end) {
        if (end >= text.length()) return null;
        int limit = Math.min(text.length(), end + 20);
        for (int i = end; i < limit; i++) {
            if (SEPARATORS.indexOf(text.charAt(i)) >= 0) {
                limit = i;
                break;
            }
        }
        String rest = text.substring(end, limit).trim();
        if (rest.isEmpty()) return null;
        Matcher matcher = TIME_TOKEN.matcher(rest);
        if (matcher.lookingAt()) {
            String tail = rest.substring(matcher.end()).trim();
            // 时间标记后还残留数字/时刻字（如“3刻”“半”“分”），说明精度无法可靠解析。
            boolean cleanTail = tail.isEmpty() || !tail.matches(".*[0-9一二三四五六七八九十点时刻分半：:].*");
            if (cleanTail) {
                return parseTimeToken(matcher);
            }
        }
        return TIME_HINT.matcher(rest).find() ? TIME_UNPARSED : null;
    }

    private static int[] parseTimeToken(Matcher matcher) {
        try {
            int hour = Integer.parseInt(matcher.group(2));
            int minute;
            if (matcher.group(3) != null) {
                minute = Integer.parseInt(matcher.group(3));
            } else if (matcher.group(4) != null) {
                minute = Integer.parseInt(matcher.group(4));
            } else if (matcher.group(6) != null) {
                // group(6) 是“点N分”里的显式分钟数字；必须先于 group(5) 判断，
                // 因为 group(5) 是整个“半|N分”外层捕获组，N 分时也会非空。
                minute = Integer.parseInt(matcher.group(6));
            } else if (matcher.group(5) != null) {
                minute = 30; // “半点”
            } else {
                minute = 0;
            }
            if (minute > 59) return TIME_UNPARSED;
            String modifier = matcher.group(1);
            if (("下午".equals(modifier) || "晚上".equals(modifier) || "中午".equals(modifier)) && hour < 12) {
                hour += 12;
            }
            if (("凌晨".equals(modifier) || "早上".equals(modifier) || "上午".equals(modifier)) && hour == 12) {
                hour = 0;
            }
            if (hour > 23) return TIME_UNPARSED;
            return new int[]{hour, minute};
        } catch (NumberFormatException ignored) {
            return TIME_UNPARSED;
        }
    }

    private static List<FoundDate> dates(String text) {
        List<FoundDate> found = new ArrayList<>();
        boolean[] used = new boolean[text.length()];

        Matcher full = FULL_DATE.matcher(text);
        while (full.find()) {
            try {
                int month = Integer.parseInt(full.group(2));
                int day = Integer.parseInt(full.group(3));
                if (!valid(month, day)) continue;
                int[] time = trailingTime(text, full.end());
                found.add(new FoundDate(Integer.parseInt(full.group(1)), month, day,
                        time == null ? -1 : time[0], time == null ? -1 : time[1],
                        time == TIME_UNPARSED, full.start(), full.end()));
                for (int i = full.start(); i < full.end() && i < used.length; i++) used[i] = true;
            } catch (NumberFormatException ignored) {
                // 不是有效数字，跳过
            }
        }

        Matcher monthDay = MONTH_DAY.matcher(text);
        while (monthDay.find()) {
            if (used[monthDay.start()]) continue;
            try {
                int month = Integer.parseInt(monthDay.group(1));
                int day = Integer.parseInt(monthDay.group(2));
                if (!valid(month, day)) continue;
                Integer inherited = inheritedYear(found, monthDay.start());
                int[] time = trailingTime(text, monthDay.end());
                found.add(new FoundDate(inherited == null ? -1 : inherited, month, day,
                        time == null ? -1 : time[0], time == null ? -1 : time[1],
                        time == TIME_UNPARSED, monthDay.start(), monthDay.end()));
            } catch (NumberFormatException ignored) {
                // 不是有效数字，跳过
            }
        }

        found.sort(Comparator.comparingInt(FoundDate::start));
        return found;
    }

    /** 区间写法（如“2026年9月1日至9月25日”）沿用最近一个完整日期的年份。 */
    private static Integer inheritedYear(List<FoundDate> found, int position) {
        Integer year = null;
        for (FoundDate candidate : found) {
            if (candidate.end <= position && position - candidate.end <= 24) {
                year = candidate.year;
            }
        }
        return year;
    }

    private static boolean valid(int month, int day) {
        return month >= 1 && month <= 12 && day >= 1 && day <= 31;
    }

    /**
     * 取日期所在分句；若该分句没有报名/开赛线索，则并入前一分句，覆盖
     * “报名截止时间为：\\n2026年9月25日”这类换行写法。
     */
    private static String contextText(String snippet, int index) {
        List<int[]> spans = new ArrayList<>();
        int start = 0;
        for (int i = 0; i <= snippet.length(); i++) {
            if (i == snippet.length() || SEPARATORS.indexOf(snippet.charAt(i)) >= 0) {
                if (i > start) spans.add(new int[]{start, i});
                start = i + 1;
            }
        }
        int current = -1;
        for (int i = 0; i < spans.size(); i++) {
            if (index >= spans.get(i)[0] && index < spans.get(i)[1]) {
                current = i;
                break;
            }
        }
        if (current < 0) return snippet;
        int[] span = spans.get(current);
        String context = snippet.substring(span[0], span[1]);
        if (!hasAnyKeyword(context) && current > 0) {
            int[] previous = spans.get(current - 1);
            context = snippet.substring(previous[0], previous[1]) + " " + context;
        }
        return context;
    }

    private static boolean hasAnyKeyword(String text) {
        return SIGNUP_WORD.matcher(text).find()
                || CUTOFF_WORD.matcher(text).find()
                || SIGNUP_START_WORD.matcher(text).find()
                || EVENT_WORD.matcher(text).find();
    }

    private static String compact(String value) {
        return value.replaceAll("[\\s《》〈〉“”\"'（）()【】\\[\\]]", "");
    }
}
