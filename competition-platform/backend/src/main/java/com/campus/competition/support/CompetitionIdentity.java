package com.campus.competition.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * 赛事实例身份：名称归一化 + 主办方 + 年份 + 届次 + 赛道。
 *
 * <p>来源 URL 只是“可能同一来源”的线索：官网经常每年复用同一个活动入口，
 * 同一篇通知也可能覆盖多个赛道。因此 URL 不能单独判定重复，必须和年份/届次/赛道一起看。</p>
 */
public final class CompetitionIdentity {

    public enum Verdict {
        /** 确定同一实例，应阻止重复创建。 */
        EXACT,
        /** 明确不同实例（新年份/新届次/不同赛道），允许创建草稿并提示来源复用。 */
        DIFFERENT,
        /** 名称相同但身份字段不完整，不能自动判定，需人工确认。 */
        INCOMPLETE,
        /** 名称不同或相似度不足，交给相似度规则处理。 */
        NONE
    }

    public record Match(Verdict verdict, int similarity, String reason) {
    }

    private CompetitionIdentity() {
    }

    /**
     * 比较待写入赛事实例与已有记录。url 需为 {@link TextSignals#normalizeUrl(String)} 的结果。
     */
    public static Match compare(String name, String organizer, Integer year, String edition, String track,
                                String normalizedUrl,
                                String otherName, String otherOrganizer, Integer otherYear,
                                String otherEdition, String otherTrack, String otherUrl) {
        String leftKey = TextSignals.nameKey(name);
        String rightKey = TextSignals.nameKey(otherName);
        int similarity = TextSignals.nameSimilarity(name, otherName);
        boolean sameName = leftKey != null && leftKey.equals(rightKey);
        boolean sameUrl = normalizedUrl != null && normalizedUrl.equals(otherUrl);
        boolean organizerKnown = notBlank(organizer) && notBlank(otherOrganizer);
        boolean sameOrganizer = organizerKnown
                && normalize(organizer).equals(normalize(otherOrganizer));

        if (!sameName) {
            if (similarity >= 95 && sameUrl && sameOrganizer) {
                return new Match(Verdict.EXACT, 100, "来源 URL 相同，赛事名称只是写法略有差异");
            }
            return new Match(Verdict.NONE, similarity, null);
        }
        if (organizerKnown && !sameOrganizer) {
            // 同名不同主办方通常是不同赛事（或学校承办层级不同），不作为确定重复。
            return new Match(Verdict.NONE, similarity, null);
        }

        boolean yearKnown = year != null && otherYear != null;
        boolean yearConflict = yearKnown && !year.equals(otherYear);
        String editionKey = editionKey(edition);
        String otherEditionKey = editionKey(otherEdition);
        boolean editionKnown = editionKey != null && otherEditionKey != null;
        boolean editionDifferent = editionKnown && !editionKey.equals(otherEditionKey);
        boolean editionNumeric = editionDifferent
                && editionKey.startsWith("n:") && otherEditionKey.startsWith("n:");
        boolean trackKnown = usableTrack(track) && usableTrack(otherTrack);
        boolean trackConflict = trackKnown && !normalize(track).equals(normalize(otherTrack));

        if (yearConflict) {
            return new Match(Verdict.DIFFERENT, similarity,
                    "名称与主办方相同，但年份不同（" + year + " / " + otherYear + "），可能是新一届");
        }
        if (editionDifferent) {
            if (editionNumeric) {
                return new Match(Verdict.DIFFERENT, similarity,
                        "名称与主办方相同，但届次不同（" + edition + " / " + otherEdition + "）");
            }
            // 届次无法可靠归一（非数字写法）时不能仅凭文本不同就断定新实例。
            return new Match(Verdict.INCOMPLETE, similarity,
                    "名称与主办方相同，届次写法无法可靠比对（" + edition + " / " + otherEdition
                            + "），需人工核验是否同一届");
        }
        if (trackConflict) {
            return new Match(Verdict.DIFFERENT, similarity,
                    "名称与年份相同，但赛道不同（" + track + " / " + otherTrack
                            + "），可能是同一赛事的不同赛道");
        }
        if (sameUrl) {
            return new Match(Verdict.EXACT, 100,
                    "来源 URL 相同，且年份/届次/赛道不冲突；如为新一届请先补全年份或届次");
        }
        if (yearKnown) {
            return new Match(Verdict.EXACT, 100,
                    "名称归一化 + 主办方 + 年份 + 赛道一致");
        }
        if (organizerKnown) {
            return new Match(Verdict.INCOMPLETE, 90,
                    "名称与主办方相同，但缺少年份/届次，无法确认是否同一届，需人工确认");
        }
        return new Match(Verdict.INCOMPLETE, 85,
                "赛事名称相同但身份字段不完整，需人工确认");
    }

    /**
     * 数据库层防并发用的身份键。身份不完整（缺名称/主办方/年份）时返回 null，
     * 不建立可能误伤“新一届”的唯一键。
     */
    public static String key(String name, String organizer, Integer year, String edition, String track) {
        String nameKey = TextSignals.nameKey(name);
        String org = normalize(organizer);
        if (nameKey == null || org == null || year == null) return null;
        String raw = nameKey + "|" + org + "|" + year
                + "|" + (editionKey(edition) == null ? "" : editionKey(edition))
                + "|" + (usableTrack(track) ? normalize(track) : "");
        return sha256(raw);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 只有像“标签”的值才参与身份比较。模型偶尔会把整句简介写进赛道，
     * 这种值不能用来证明“这是另一条赛道”，否则同一赛事会被判成不同实例。
     */
    private static boolean usableTrack(String value) {
        if (!notBlank(value)) return false;
        String trimmed = value.trim();
        return trimmed.length() <= 20 && !trimmed.matches(".*[。；;，,].*");
    }

    private static boolean usableEdition(String value) {
        if (!notBlank(value)) return false;
        String trimmed = value.trim();
        return trimmed.length() <= 20 && !trimmed.matches(".*[。；;，,].*");
    }

    /**
     * 届次归一：去掉年份前缀、“第”和末尾“届”，再把可可靠解析的届次统一为数值，
     * 让“2026年第八届”“第八届”“第8届”“八届”“8届”视为同一届（n:8）。
     * 无法解析的写法保留归一化文本（t:...），只有完全一致才算同一届，
     * 不同写法交人工核验，不能仅凭文本不同就断定新实例。
     */
    private static String editionKey(String value) {
        if (!usableEdition(value)) return null;
        String stripped = value.trim().replaceAll("\\s+", "")
                .replaceAll("^20\\d{2}\\s*年?", "")
                .replaceAll("^第", "")
                .replaceAll("届$", "");
        if (stripped.isEmpty()) return null;
        Integer number = editionNumber(stripped);
        if (number != null) return "n:" + number;
        return "t:" + stripped.toLowerCase(Locale.ROOT);
    }

    private static Integer editionNumber(String text) {
        if (text.matches("\\d{1,3}")) {
            return Integer.parseInt(text);
        }
        return parseChineseNumeral(text);
    }

    /** 中文数字届次（一～九十九），无法可靠解析返回 null。 */
    private static Integer parseChineseNumeral(String text) {
        java.util.Map<Character, Integer> digits = java.util.Map.ofEntries(
                java.util.Map.entry('零', 0), java.util.Map.entry('〇', 0),
                java.util.Map.entry('一', 1), java.util.Map.entry('二', 2),
                java.util.Map.entry('两', 2), java.util.Map.entry('三', 3),
                java.util.Map.entry('四', 4), java.util.Map.entry('五', 5),
                java.util.Map.entry('六', 6), java.util.Map.entry('七', 7),
                java.util.Map.entry('八', 8), java.util.Map.entry('九', 9));
        if ("十".equals(text)) return 10;
        if (text.length() == 1) {
            return digits.get(text.charAt(0));
        }
        int ten = text.indexOf('十');
        if (ten < 0 || text.length() > 3) return null;
        Integer tens = ten == 0 ? 1 : (ten == 1 ? digits.get(text.charAt(0)) : null);
        Integer ones = ten == text.length() - 1 ? 0
                : (ten == text.length() - 2 ? digits.get(text.charAt(ten + 1)) : null);
        if (tens == null || ones == null) return null;
        return tens * 10 + ones;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
