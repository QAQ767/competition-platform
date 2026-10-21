package com.campus.competition.service;

import com.campus.competition.advice.AdvicePreferences;
import com.campus.competition.advice.ParticipantProfile;
import com.campus.competition.entity.Competition;
import com.campus.competition.entity.Team;
import com.campus.competition.entity.TeamMember;
import com.campus.competition.support.TextSignals;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 参赛推荐的资格、日期与可解释评分规则。
 *
 * 设计原则：
 * 1. 只使用数据库中真实字段和用户明确填写的内容，没有证据的维度不补分；
 * 2. 分数按“已知维度”归一化，并单独返回证据完整度；
 * 3. 截止时刻本身即不可报名；缺少日期单独归类为“需确认”，不当成无限期；
 * 4. 规则先召回，模型只允许重排这批 ID，不能添加不存在的赛事或队伍。
 */
@Service
public class RecommendationEligibilityService {

    public enum Category {
        OPEN, UPCOMING, NEEDS_CONFIRM, APPLIED, EXCLUDED
    }

    private static final Pattern WEEKLY_HOURS = Pattern.compile(
            "每(?:周|星期|周均)[^。；;\\n]{0,14}?(\\d{1,2})\\s*(?:个)?\\s*小时");
    private static final Pattern EXPERIENCE_REQUIREMENT = Pattern.compile(
            "(?:需|要求|必须|希望|限)[^。；;\\n]{0,12}(?:获奖|参赛|项目)经历|有(?:获奖|参赛)经历");
    /** 缺少报名开始时间时唯一可接受“已开放”的依据：正文明确写“即日起报名/已开放报名”。 */
    private static final Pattern OPEN_NOW = Pattern.compile(
            "即日起(?:开始)?(?:接受|开放|受理)?报名|(?:现已|已经|已)开放报名|报名(?:系统)?(?:现已|已)开放");

    private final Clock clock;

    public RecommendationEligibilityService(Clock businessClock) {
        this.clock = businessClock;
    }

    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    public static final class CompetitionRule {
        public final Competition competition;
        public Category category = Category.OPEN;
        public int score;
        public double completeness;
        public boolean weakDirection;
        public String excludeReason;
        public final List<String> reasons = new ArrayList<>();
        public final List<String> risks = new ArrayList<>();
        public String nextStep;

        CompetitionRule(Competition competition) {
            this.competition = competition;
        }

        public boolean recommendable() {
            return category == Category.OPEN || category == Category.UPCOMING || category == Category.NEEDS_CONFIRM;
        }
    }

    public static final class TeamRule {
        public final Team team;
        public Category category = Category.OPEN;
        public int score;
        public double completeness;
        public String excludeReason;
        public String suggestedRole;
        public final List<String> reasons = new ArrayList<>();
        public final List<String> risks = new ArrayList<>();
        public String nextStep;
        public int remainingPlaces;
        public List<String> requiredSkills = new ArrayList<>();
        public List<String> memberSkillSummary = new ArrayList<>();
        public String competitionName;
        public LocalDateTime competitionSignupEnd;

        TeamRule(Team team) {
            this.team = team;
        }

        public boolean recommendable() {
            return category == Category.OPEN || category == Category.NEEDS_CONFIRM;
        }
    }

    // ==================== 竞赛 ====================

    public CompetitionRule assessCompetition(Competition competition, ParticipantProfile profile,
                                             AdvicePreferences preferences, int windowDays) {
        CompetitionRule rule = new CompetitionRule(competition);
        if (preferences.getExcludedCompetitionIds().contains(competition.getId())) return exclude(rule, "本轮更换已展示赛事");
        if ("UNRESOLVED".equals(preferences.getCompetitionIntentStatus()) || "AMBIGUOUS".equals(preferences.getCompetitionIntentStatus())) return exclude(rule, "指定赛事待确认，不能放宽条件");
        if (!preferences.getResolvedCompetitionIds().isEmpty() && !preferences.getResolvedCompetitionIds().contains(competition.getId())) return exclude(rule, "不属于指定赛事");
        if (preferences.getResolvedCompetitionIds().isEmpty() && !preferences.getCompetitionKeywords().isEmpty() && preferences.getCompetitionKeywords().stream()
                .noneMatch(k -> competition.getName() != null && competition.getName().toLowerCase(Locale.ROOT).contains(k.toLowerCase(Locale.ROOT)))) return exclude(rule, "不属于指定赛事");
        LocalDateTime now = now();
        LocalDateTime windowEnd = preferences.isWindowUnrestricted() ? LocalDateTime.MAX : now.plusDays(windowDays);
        String corpus = join(competition.getName(), competition.getDescription(),
                competition.getTags(), competition.getEligibilityText());

        // 1. 用户明确排除的方向：硬过滤，模型也不能加回来。
        for (String excluded : preferences.getExcludedKeywords()) {
            if (excluded == null || excluded.isBlank()) continue;
            String canonical = TextSignals.canonicalDirection(excluded);
            String needle = excluded.toLowerCase(Locale.ROOT);
            boolean hit = corpus.contains(needle)
                    || (canonical != null && TextSignals.mentions(corpus, canonical));
            if (hit) {
                return exclude(rule, "你明确排除了「" + excluded + "」，该赛事名称/标签/条件中出现相关词");
            }
        }

        // 2. 只处理能确认的资格限制；无法从档案判断的写入待确认，不擅自剔除。
        String mismatch = eligibilityMismatch(competition.getEligibilityText(), profile);
        if (mismatch != null) {
            return exclude(rule, mismatch);
        }

        LocalDateTime start = competition.getSignupStart();
        LocalDateTime end = competition.getSignupEnd();

        if (end != null && !end.isAfter(now)) {
            rule.category = Category.EXCLUDED;
            rule.excludeReason = "报名已于 " + end.toLocalDate() + " 截止（不代表整场比赛已经结束）";
            return rule;
        }
        if (start != null && end != null && start.isAfter(end)) {
            rule.category = Category.NEEDS_CONFIRM;
            rule.risks.add("报名开始时间晚于报名截止时间，日期可能录入有误，需人工核对");
        } else if (end == null) {
            rule.category = Category.NEEDS_CONFIRM;
            if (start == null) {
                rule.risks.add("报名起止时间均未公布，不能视为仍在报名");
            } else {
                rule.risks.add("报名截止时间未公布，不能视为仍在报名");
            }
            if (start != null && start.isAfter(windowEnd)) {
                rule.category = Category.EXCLUDED;
                rule.excludeReason = "报名开始时间超出本次 " + windowDays + " 天范围";
                return rule;
            }
        } else if (start != null && start.isAfter(now)) {
            if (start.isAfter(windowEnd)) {
                rule.category = Category.EXCLUDED;
                rule.excludeReason = "报名开始时间超出本次 " + windowDays + " 天范围";
                return rule;
            }
            rule.category = Category.UPCOMING;
            rule.nextStep = "先做赛前准备，报名开放后再提交";
        } else if (start == null) {
            // 只有截止时间不能证明“现在已开放”：可能是尚未启动的报名。
            String openEvidence = declaresOpenNow(competition);
            if (openEvidence != null) {
                rule.category = Category.OPEN;
                rule.reasons.add("赛事说明写明「" + openEvidence + "」，据此判定当前已开放报名（依据为文字描述）");
                rule.risks.add("报名开始时间未录入，开放依据来自文字描述，报名入口需再次核对");
                rule.nextStep = "查看赛事详情并确认报名入口";
            } else {
                rule.category = Category.NEEDS_CONFIRM;
                rule.risks.add("报名开始时间未公布，无法确认当前是否已开放，需向主办方确认");
                rule.nextStep = "先向主办方确认报名是否已开放";
            }
        } else {
            rule.category = Category.OPEN;
            rule.nextStep = "查看赛事详情并核对参赛条件";
        }

        scoreCompetition(rule, profile, preferences);
        if (rule.category == Category.NEEDS_CONFIRM) {
            rule.score = (int) Math.round(rule.score * 0.75);
        }
        return rule;
    }

    private CompetitionRule exclude(CompetitionRule rule, String reason) {
        rule.category = Category.EXCLUDED;
        rule.excludeReason = reason;
        return rule;
    }

    /** 返回命中的原文短语；没有明确“即日起/已开放报名”时返回 null。 */
    private String declaresOpenNow(Competition competition) {
        String corpus = join(competition.getDescription(), competition.getEligibilityText());
        if (corpus.isBlank()) return null;
        Matcher matcher = OPEN_NOW.matcher(corpus);
        return matcher.find() ? matcher.group() : null;
    }

    private void scoreCompetition(CompetitionRule rule, ParticipantProfile profile, AdvicePreferences preferences) {
        Competition competition = rule.competition;
        Set<String> userCapabilities = new LinkedHashSet<>(profile.capabilities());
        userCapabilities.addAll(TextSignals.capabilities(
                String.join(",", preferences.getDirections()),
                String.join(",", preferences.getSkills()),
                String.join(",", preferences.getRoles())));

        List<String> officialTags = TextSignals.splitTags(competition.getTags());
        List<String> tags = officialTags;
        if (tags.isEmpty()) {
            tags = new ArrayList<>(TextSignals.capabilities(competition.getName(), competition.getDescription()));
            rule.weakDirection = true;
        }

        int directionMax = 35;
        int direction = -1;
        if (!tags.isEmpty()) {
            List<String> matched = new ArrayList<>();
            for (String tag : tags) {
                String canonical = TextSignals.canonicalDirection(tag);
                if (userCapabilities.contains(canonical) || userCapabilities.contains(tag)
                        || TextSignals.mentions(profile.getIntro() + " " + profile.getProjectExperience(), tag)) {
                    matched.add(tag);
                }
            }
            if (officialTags.isEmpty()) {
                direction = matched.isEmpty() ? 0 : Math.min(18,
                        (int) Math.round(18.0 * matched.size() / Math.max(1, tags.size())) + 6);
                if (!matched.isEmpty()) {
                    rule.reasons.add("赛事未维护方向标签，按名称/简介关键词推断为「"
                            + String.join("、", tags) + "」，属于弱匹配证据");
                } else {
                    rule.risks.add("赛事未维护方向标签，名称/简介关键词也未命中你的能力");
                }
            } else {
                direction = (int) Math.round(directionMax * matched.size() / (double) tags.size());
                if (!matched.isEmpty()) {
                    rule.reasons.add("赛事方向标签：" + String.join("、", officialTags)
                            + "；你的技能/经历覆盖：" + String.join("、", matched));
                } else {
                    rule.risks.add("你的技能与赛事方向标签没有直接交集");
                }
            }
        } else {
            rule.risks.add("赛事没有方向标签，无法判断方向匹配");
        }

        int skillMax = 30;
        int skill = -1;
        Set<String> declared = new LinkedHashSet<>(profile.declaredSkills());
        declared.addAll(preferences.getSkills());
        if (!declared.isEmpty() || profile.hasProjectEvidence()) {
            List<String> hitSkills = new ArrayList<>();
            for (String need : tags) {
                for (String declaredSkill : declared) {
                    if (need.equalsIgnoreCase(declaredSkill) || need.contains(declaredSkill) || declaredSkill.contains(need)) {
                        hitSkills.add(declaredSkill);
                        break;
                    }
                }
            }
            if (!tags.isEmpty()) {
                skill = (int) Math.round(20.0 * hitSkills.size() / tags.size());
                if (!hitSkills.isEmpty()) {
                    rule.reasons.add("你的技能标签命中：" + String.join("、", hitSkills));
                }
            } else {
                skill = -1;
            }
            if (skill >= 0 && profile.hasProjectEvidence()) {
                skill = Math.min(skillMax, skill + (profile.getAchievements().isEmpty() ? 6 : 10));
                rule.reasons.add(profile.getAchievements().isEmpty()
                        ? "档案中有项目经历，可作为能力佐证"
                        : "档案中有获奖记录，可作为能力佐证");
            }
        } else {
            rule.risks.add("档案没有技能标签和项目经历，无法评估能力匹配");
        }

        int timeMax = 20;
        int time = -1;
        LocalDateTime deadline = competition.getSignupEnd() != null
                ? competition.getSignupEnd() : competition.getSignupStart();
        Integer hours = effectiveWeeklyHours(profile, preferences);
        if (deadline != null && hours != null) {
            long days = Math.max(0, ChronoUnit.DAYS.between(now(), deadline));
            double capacity = Math.max(1, days) / 7.0 * hours;
            time = capacity >= 18 ? 20 : capacity >= 10 ? 17 : capacity >= 5 ? 14 : capacity >= 2 ? 10 : 6;
            rule.reasons.add("每周可投入 " + hours + " 小时，距报名截止/开始约 " + days + " 天");
            if (days <= 3) {
                rule.risks.add("报名时间只剩 " + days + " 天，准备时间很紧");
            }
        } else if (hours == null) {
            rule.risks.add("每周可投入时间未确定（0 表示未确定），时间适配未计分");
        }

        int preferenceMax = 15;
        int preference = 0;
        String status = profile.getCompeteStatus();
        if ("WANT_INVITE".equals(status)) {
            preference = 12;
            rule.reasons.add("你的参赛意向为“希望被邀请/正在备战”");
        } else if ("ACCEPT_INVITE".equals(status)) {
            preference = 10;
            rule.reasons.add("你的参赛意向为“可接受邀请”");
        } else if ("NOT_PARTICIPATE".equals(status) || "ONLY_VIEW".equals(status)) {
            preference = 3;
            rule.risks.add("你当前参赛意向为“不参赛/仅浏览”，本次推荐不会修改你的公开状态");
        } else {
            preference = 6;
        }
        for (String preferredDirection : preferences.getDirections()) {
            String canonical = TextSignals.canonicalDirection(preferredDirection);
            if (canonical != null && tags.contains(canonical)) {
                preference = Math.min(preferenceMax, preference + 3);
                rule.reasons.add("你在本次对话中明确关注「" + preferredDirection + "」");
                break;
            }
        }
        if (preferences.getDifficulty() != null && competition.getLevel() != null) {
            String difficulty = preferences.getDifficulty();
            boolean match = ("入门".equals(difficulty) && "校级".equals(competition.getLevel()))
                    || ("进阶".equals(difficulty)
                    && ("省级".equals(competition.getLevel()) || "国家级".equals(competition.getLevel())));
            if (match) {
                preference = Math.min(preferenceMax, preference + 2);
            }
        }

        applyScore(rule, new int[]{direction, skill, time, preference},
                new int[]{directionMax, skillMax, timeMax, preferenceMax});

        if (competition.getEligibilityText() == null || competition.getEligibilityText().isBlank()) {
            rule.risks.add("赛事未填写参赛条件，报名前需核对资格");
        }
        if (rule.nextStep == null) {
            rule.nextStep = "查看赛事详情并核对报名时间与参赛条件";
        }
    }

    private void applyScore(CompetitionRule rule, int[] values, int[] max) {
        int raw = 0;
        int knownMax = 0;
        int known = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] >= 0) {
                raw += values[i];
                knownMax += max[i];
                known++;
            }
        }
        rule.score = knownMax == 0 ? 0 : (int) Math.round(raw * 100.0 / knownMax);
        rule.completeness = known / (double) max.length;
    }

    private void applyScore(TeamRule rule, int[] values, int[] max) {
        int raw = 0;
        int knownMax = 0;
        int known = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] >= 0) {
                raw += values[i];
                knownMax += max[i];
                known++;
            }
        }
        rule.score = knownMax == 0 ? 0 : (int) Math.round(raw * 100.0 / knownMax);
        rule.completeness = known / (double) max.length;
    }

    /**
     * 只解析能确定判断的专业/学院限制。例如“仅限软件工程专业”且档案专业不是软件工程时剔除；
     * 档案没有专业时不下结论。
     */
    String eligibilityMismatch(String eligibility, ParticipantProfile profile) {
        if (eligibility == null || eligibility.isBlank()) return null;
        Matcher major = Pattern.compile("仅限([\\u4e00-\\u9fa5A-Za-z]{2,12})专业").matcher(eligibility);
        if (major.find() && profile.getMajor() != null && !profile.getMajor().isBlank()) {
            String required = major.group(1);
            if (!profile.getMajor().contains(required) && !required.contains(profile.getMajor())) {
                return "参赛条件写明仅限「" + required + "专业」，与你的专业「" + profile.getMajor() + "」不符";
            }
        }
        Matcher college = Pattern.compile("仅限([\\u4e00-\\u9fa5A-Za-z]{2,12})学院").matcher(eligibility);
        if (college.find() && profile.getCollege() != null && !profile.getCollege().isBlank()) {
            String required = college.group(1);
            if (!profile.getCollege().contains(required) && !required.contains(profile.getCollege())) {
                return "参赛条件写明仅限「" + required + "学院」，与你的学院「" + profile.getCollege() + "」不符";
            }
        }
        return null;
    }

    // ==================== 队伍 ====================

    public TeamRule assessTeam(Team team, Competition competition, List<TeamMember> members,
                               Map<Long, Set<String>> memberCapabilities, ParticipantProfile profile,
                               AdvicePreferences preferences, boolean alreadyApplied) {
        TeamRule rule = new TeamRule(team);
        if (preferences.getExcludedTeamIds().contains(team.getId())) return exclude(rule, "本轮更换已展示队伍");
        if (competition != null) {
            AdvicePreferences associated = preferences.copy(); associated.setExcludedCompetitionIds(List.of());
            var compRule = assessCompetition(competition, profile, associated, preferences.getWindowDays() == null ? 90 : preferences.getWindowDays());
            if (!compRule.recommendable()) return exclude(rule, compRule.excludeReason);
        } else if (!preferences.getCompetitionKeywords().isEmpty()) return exclude(rule, "无法核查所属赛事");
        LocalDateTime now = now();
        rule.competitionName = competition == null ? null : competition.getName();
        rule.competitionSignupEnd = competition == null ? null : competition.getSignupEnd();
        int actualMembers = members == null ? 0 : members.size();
        rule.remainingPlaces = Math.max(0, (team.getMaxMembers() == null ? 0 : team.getMaxMembers()) - actualMembers);

        String teamCorpus = join(team.getTitle(), team.getDescription(), team.getSkills(),
                competition == null ? null : competition.getName(),
                competition == null ? null : competition.getTags(),
                competition == null ? null : competition.getDescription());
        for (String excluded : preferences.getExcludedKeywords()) {
            if (excluded == null || excluded.isBlank()) continue;
            String canonical = TextSignals.canonicalDirection(excluded);
            if (teamCorpus.contains(excluded.toLowerCase(Locale.ROOT))
                    || (canonical != null && TextSignals.mentions(teamCorpus, canonical))) {
                return exclude(rule, "你明确排除了「" + excluded + "」，该队伍或所属赛事出现相关词");
            }
        }

        if (alreadyApplied) {
            rule.category = Category.APPLIED;
            rule.excludeReason = "你已经申请或收到该队伍的邀请，等待处理即可";
            return rule;
        }
        if (team.getCaptainId() != null && team.getCaptainId().equals(profile.getUserId())) {
            return exclude(rule, "这是你担任队长的队伍");
        }
        if (members != null && members.stream().anyMatch(m -> profile.getUserId().equals(m.getUserId()))) {
            return exclude(rule, "你已经是该队伍成员");
        }
        String blocker = TeamRecruitmentGuard.joinBlocker(team, competition, actualMembers, now);
        if (blocker != null) {
            return exclude(rule, blocker);
        }
        if (team.getDeadline() == null) {
            rule.risks.add("队伍未设置招募截止时间，需向队长确认");
        }
        if (competition == null) {
            rule.risks.add("队伍关联的赛事不存在或已删除");
        } else if (competition.getSignupEnd() == null) {
            rule.risks.add("所属赛事报名截止时间未公布，需确认");
        }

        // 队伍明确写出的硬性要求才做硬限制。
        Integer requiredHours = weeklyHoursRequirement(team.getDescription());
        Integer userHours = effectiveWeeklyHours(profile, preferences);
        if (requiredHours != null) {
            if (userHours != null && userHours < requiredHours) {
                return exclude(rule, "队伍简介写明每周至少 " + requiredHours + " 小时，你当前为 " + userHours + " 小时");
            }
            if (userHours == null) {
                rule.risks.add("队伍要求每周至少 " + requiredHours + " 小时，你的可投入时间未确定");
            }
        }
        if (EXPERIENCE_REQUIREMENT.matcher(team.getDescription() == null ? "" : team.getDescription()).find()
                && !profile.hasProjectEvidence()) {
            return exclude(rule, "队伍明确要求相关经历，你的档案还没有项目或参赛经历");
        }

        List<String> required = TextSignals.splitTags(team.getSkills());
        List<String> tags = required;
        boolean weak = false;
        if (tags.isEmpty()) {
            tags = new ArrayList<>(TextSignals.capabilities(team.getTitle(), team.getDescription()));
            weak = true;
            rule.risks.add("队伍未填写所需技能，只能按标题/简介关键词弱匹配");
        }
        rule.requiredSkills = required;
        Set<String> memberCaps = new LinkedHashSet<>();
        if (memberCapabilities != null) {
            memberCapabilities.values().forEach(memberCaps::addAll);
        }
        rule.memberSkillSummary = new ArrayList<>(memberCaps);

        Set<String> userDeclared = new LinkedHashSet<>(profile.declaredSkills());
        userDeclared.addAll(preferences.getSkills());
        Set<String> userCaps = new LinkedHashSet<>(profile.capabilities());
        userCaps.addAll(TextSignals.capabilities(
                String.join(",", preferences.getDirections()),
                String.join(",", preferences.getSkills()),
                String.join(",", preferences.getRoles())));

        // 技能覆盖 30
        int skill = -1;
        if (!tags.isEmpty() && (!userDeclared.isEmpty() || !userCaps.isEmpty())) {
            List<String> hit = new ArrayList<>();
            List<String> gap = new ArrayList<>();
            for (String need : tags) {
                String canonical = TextSignals.canonicalDirection(need);
                boolean covered = userDeclared.stream().anyMatch(s -> s.equalsIgnoreCase(need)
                        || s.contains(need) || need.contains(s))
                        || userCaps.contains(canonical);
                if (covered) hit.add(need); else gap.add(need);
            }
            skill = (int) Math.round(30.0 * hit.size() / tags.size());
            if (!hit.isEmpty()) {
                rule.reasons.add("你具备队伍所需技能：" + String.join("、", hit));
            }
            if (!gap.isEmpty()) {
                rule.risks.add("你尚未体现队伍需要的：" + String.join("、", gap));
            }
        }

        // 角色互补 30：看现有成员未覆盖、而你能补上的部分
        int complement = -1;
        if (!tags.isEmpty()) {
            List<String> memberCovered = new ArrayList<>();
            List<String> uncovered = new ArrayList<>();
            for (String need : tags) {
                String canonical = TextSignals.canonicalDirection(need);
                if (memberCaps.contains(canonical)) memberCovered.add(need);
                else uncovered.add(need);
            }
            if (uncovered.isEmpty()) {
                complement = 8;
                rule.risks.add("队伍所需技能已被现有成员基本覆盖，互补空间有限");
            } else {
                long filled = uncovered.stream().filter(need -> {
                    String canonical = TextSignals.canonicalDirection(need);
                    return userCaps.contains(canonical)
                            || userDeclared.stream().anyMatch(s -> s.equalsIgnoreCase(need)
                            || s.contains(need) || need.contains(s));
                }).count();
                complement = (int) Math.round(30.0 * filled / uncovered.size());
                if (filled > 0) {
                    rule.reasons.add("现有成员能力摘要未覆盖：" + String.join("、", uncovered)
                            + "；你可以补位其中的 " + filled + " 项");
                }
            }
            rule.suggestedRole = suggestRole(uncovered, tags, userCaps, userDeclared, preferences);
        } else {
            rule.suggestedRole = preferences.getRoles().isEmpty() ? "队员（待沟通）" : preferences.getRoles().get(0);
        }

        // 方向 15
        int direction = -1;
        if (competition != null) {
            List<String> compTags = TextSignals.splitTags(competition.getTags());
            if (compTags.isEmpty()) {
                compTags = new ArrayList<>(TextSignals.capabilities(competition.getName(), competition.getDescription()));
            }
            if (!compTags.isEmpty()) {
                long hit = compTags.stream().filter(t -> {
                    String canonical = TextSignals.canonicalDirection(t);
                    return userCaps.contains(canonical);
                }).count();
                direction = (int) Math.round(15.0 * hit / compTags.size());
                if (hit > 0) {
                    rule.reasons.add("队伍目标赛事方向：" + String.join("、", compTags)
                            + "，与你的能力有交集");
                }
            }
        }

        // 时间 15
        int time = -1;
        if (team.getDeadline() != null && userHours != null) {
            long days = Math.max(0, ChronoUnit.DAYS.between(now, team.getDeadline()));
            double capacity = Math.max(1, days) / 7.0 * userHours;
            time = capacity >= 10 ? 15 : capacity >= 5 ? 12 : capacity >= 2 ? 9 : 5;
            rule.reasons.add("每周可投入 " + userHours + " 小时，距组队截止约 " + days + " 天");
            if (days <= 3) rule.risks.add("组队截止只剩 " + days + " 天");
        } else if (userHours == null) {
            rule.risks.add("每周可投入时间未确定（0 表示未确定），时间适配未计分");
        }

        // 明确偏好 10
        int preference = 0;
        String status = profile.getCompeteStatus();
        if ("WANT_INVITE".equals(status)) preference = 8;
        else if ("ACCEPT_INVITE".equals(status)) preference = 7;
        else if ("NOT_PARTICIPATE".equals(status) || "ONLY_VIEW".equals(status)) {
            preference = 2;
            rule.risks.add("你当前参赛意向为“不参赛/仅浏览”，申请前请确认是否真的参加");
        } else preference = 4;
        boolean roleHit = !preferences.getRoles().isEmpty()
                && tags.stream().anyMatch(tag -> preferences.getRoles().stream().anyMatch(
                role -> tag.contains(role) || role.contains(tag)));
        if (roleHit) preference = Math.min(10, preference + 2);
        if (Boolean.TRUE.equals(preferences.getTeamOnly())) preference = Math.min(10, preference + 1);

        applyScore(rule, new int[]{direction, skill, complement, time, preference},
                new int[]{15, 30, 30, 15, 10});

        if (weak) {
            rule.score = (int) Math.round(rule.score * 0.85);
        }
        if (rule.nextStep == null) {
            rule.nextStep = rule.category == Category.NEEDS_CONFIRM
                    ? "先向队长确认赛事报名与组队截止时间"
                    : "查看队伍详情并主动提交申请";
        }
        if (rule.competitionSignupEnd == null && competition != null) {
            rule.category = Category.NEEDS_CONFIRM;
        }
        return rule;
    }

    private TeamRule exclude(TeamRule rule, String reason) {
        rule.category = Category.EXCLUDED;
        rule.excludeReason = reason;
        return rule;
    }

    private String suggestRole(List<String> uncovered, List<String> tags, Set<String> userCaps,
                               Set<String> userDeclared, AdvicePreferences preferences) {
        for (String need : uncovered) {
            String canonical = TextSignals.canonicalDirection(need);
            if (userCaps.contains(canonical)
                    || userDeclared.stream().anyMatch(s -> s.equalsIgnoreCase(need)
                    || s.contains(need) || need.contains(s))) {
                return need + "（补位）";
            }
        }
        for (String need : tags) {
            String canonical = TextSignals.canonicalDirection(need);
            if (userCaps.contains(canonical)) return need;
        }
        if (!preferences.getRoles().isEmpty()) return preferences.getRoles().get(0);
        return "队员（待沟通）";
    }

    /** 本次已确认的每周时间优先于档案；没有对话覆盖时才使用档案值。 */
    private Integer effectiveWeeklyHours(ParticipantProfile profile, AdvicePreferences preferences) {
        if (preferences.isWeeklyHoursUnrestricted()) return null;
        return preferences.getWeeklyHours() != null
                ? preferences.getWeeklyHours() : profile.effectiveWeeklyHours();
    }

    Integer weeklyHoursRequirement(String description) {
        if (description == null || description.isBlank()) return null;
        Matcher matcher = WEEKLY_HOURS.matcher(description);
        if (matcher.find()) {
            try {
                int value = Integer.parseInt(matcher.group(1));
                return value > 0 && value <= 80 ? value : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String join(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part != null && !part.isBlank()) sb.append(part).append(' ');
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }
}
