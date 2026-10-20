package com.campus.competition.service;

import com.campus.competition.advice.AdvicePreferences;
import com.campus.competition.advice.ParticipantProfile;
import com.campus.competition.entity.Competition;
import com.campus.competition.entity.Team;
import com.campus.competition.entity.TeamMember;
import com.campus.competition.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 固定时钟下的资格、日期、时间与互补规则，不依赖当天日期。 */
class RecommendationEligibilityServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 20, 12, 0);
    private RecommendationEligibilityService service;
    private ParticipantProfile frontend;

    @BeforeEach
    void setUp() {
        service = new RecommendationEligibilityService(
                Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE));
        User user = new User();
        user.setId(7L);
        user.setNickname("前端同学");
        user.setMajor("软件工程");
        user.setCollege("计算机学院");
        user.setPreferredRoles("前端");
        user.setWeeklyHours(8);
        user.setCompeteStatus("WANT_INVITE");
        user.setIntro("喜欢 Vue 与交互设计");
        user.setProjectExperience("做过校园预约系统");
        frontend = ParticipantProfile.of(user, List.of("前端", "UI设计"), List.of());
    }

    @Test
    @DisplayName("报名截止时刻本身不可报名，下一秒仍可报名")
    void signupDeadlineBoundary() {
        Competition ended = competition(1L, "边界赛", NOW, NOW, NOW.plusDays(30));
        assertEquals(RecommendationEligibilityService.Category.EXCLUDED,
                service.assessCompetition(ended, frontend, new AdvicePreferences(), 90).category);

        Competition open = competition(2L, "开放赛", NOW.minusDays(2), NOW.plusSeconds(1), NOW.plusDays(30));
        assertEquals(RecommendationEligibilityService.Category.OPEN,
                service.assessCompetition(open, frontend, new AdvicePreferences(), 90).category);
    }

    @Test
    @DisplayName("未来报名归为可提前准备，缺少截止时间归为需确认")
    void upcomingAndMissingDates() {
        Competition upcoming = competition(1L, "未来赛", NOW.plusDays(10), NOW.plusDays(30), NOW.plusDays(40));
        assertEquals(RecommendationEligibilityService.Category.UPCOMING,
                service.assessCompetition(upcoming, frontend, new AdvicePreferences(), 90).category);

        Competition missingEnd = competition(2L, "日期待定", NOW.minusDays(1), null, null);
        RecommendationEligibilityService.CompetitionRule rule =
                service.assessCompetition(missingEnd, frontend, new AdvicePreferences(), 90);
        assertEquals(RecommendationEligibilityService.Category.NEEDS_CONFIRM, rule.category);
        assertTrue(rule.risks.stream().anyMatch(r -> r.contains("不能视为仍在报名")));
    }

    @Test
    @DisplayName("明确排除硬件方向时直接过滤，规则理由可解释")
    void excludesExplicitlyExcludedDirection() {
        Competition hardware = competition(1L, "智能硬件挑战赛", NOW.minusDays(1), NOW.plusDays(20), NOW.plusDays(30));
        hardware.setTags("硬件,嵌入式");
        AdvicePreferences preferences = new AdvicePreferences();
        preferences.setExcludedKeywords(new ArrayList<>(List.of("硬件")));
        RecommendationEligibilityService.CompetitionRule rule =
                service.assessCompetition(hardware, frontend, preferences, 90);
        assertEquals(RecommendationEligibilityService.Category.EXCLUDED, rule.category);
        assertTrue(rule.excludeReason.contains("明确排除"));
    }

    @Test
    @DisplayName("专业限制只在档案可判断时剔除，缺专业时只提示待确认")
    void eligibilityMajorMismatch() {
        Competition restricted = competition(1L, "专业赛", NOW.minusDays(1), NOW.plusDays(20), NOW.plusDays(30));
        restricted.setEligibilityText("仅限英语专业学生参加");
        assertEquals(RecommendationEligibilityService.Category.EXCLUDED,
                service.assessCompetition(restricted, frontend, new AdvicePreferences(), 90).category);

        User unknownMajor = new User();
        unknownMajor.setId(9L);
        ParticipantProfile noMajor = ParticipantProfile.of(unknownMajor, List.of(), List.of());
        assertTrue(service.assessCompetition(restricted, noMajor, new AdvicePreferences(), 90).recommendable());
    }

    @Test
    @DisplayName("weeklyHours=0 视为未确定，不当作完全没有时间")
    void zeroWeeklyHoursMeansUnknown() {
        User user = new User();
        user.setId(8L);
        user.setWeeklyHours(0);
        ParticipantProfile profile = ParticipantProfile.of(user, List.of("前端"), List.of());
        Competition competition = competition(1L, "前端赛", NOW.minusDays(1), NOW.plusDays(20), NOW.plusDays(30));
        RecommendationEligibilityService.CompetitionRule rule =
                service.assessCompetition(competition, profile, new AdvicePreferences(), 90);
        assertTrue(rule.risks.stream().anyMatch(r -> r.contains("0 表示未确定")));
        assertTrue(rule.completeness < 1.0);
    }

    @Test
    @DisplayName("队伍满员、队长本人、已申请、截止都被排除")
    void teamHardFilters() {
        Team team = team(10L, 1L, "招募中", 3, 3);
        assertEquals(RecommendationEligibilityService.Category.EXCLUDED,
                service.assessTeam(team, null, members(3), Map.of(), frontend, new AdvicePreferences(), false).category);

        Team captain = team(11L, 7L, "招募中", 1, 3);
        assertEquals(RecommendationEligibilityService.Category.EXCLUDED,
                service.assessTeam(captain, null, members(1), Map.of(), frontend, new AdvicePreferences(), false).category);

        Team applied = team(12L, 1L, "招募中", 1, 3);
        assertEquals(RecommendationEligibilityService.Category.APPLIED,
                service.assessTeam(applied, null, members(1), Map.of(), frontend, new AdvicePreferences(), true).category);

        Team expired = team(13L, 1L, "招募中", 1, 3);
        expired.setDeadline(NOW);
        assertEquals(RecommendationEligibilityService.Category.EXCLUDED,
                service.assessTeam(expired, null, members(1), Map.of(), frontend, new AdvicePreferences(), false).category);
    }

    @Test
    @DisplayName("队伍明确写出的每周时间要求才做硬限制")
    void explicitWeeklyRequirementIsHardLimit() {
        Team team = team(10L, 1L, "招募中", 1, 3);
        team.setDescription("需要前端，每周至少 10 小时");
        team.setSkills("前端");
        RecommendationEligibilityService.TeamRule rule =
                service.assessTeam(team, null, members(1), Map.of(), frontend, new AdvicePreferences(), false);
        assertEquals(RecommendationEligibilityService.Category.EXCLUDED, rule.category);
        assertTrue(rule.excludeReason.contains("每周至少 10 小时"));

        User unknown = new User();
        unknown.setId(8L);
        unknown.setWeeklyHours(0);
        ParticipantProfile noHours = ParticipantProfile.of(unknown, List.of("前端"), List.of());
        RecommendationEligibilityService.TeamRule risk =
                service.assessTeam(team, null, members(1), Map.of(), noHours, new AdvicePreferences(), false);
        assertTrue(risk.recommendable());
        assertTrue(risk.risks.stream().anyMatch(r -> r.contains("未确定")));
    }

    @Test
    @DisplayName("互补评分只看现有成员未覆盖、而用户能补上的技能")
    void complementScoreUsesRealMemberGap() {
        Team team = team(10L, 1L, "招募中", 1, 3);
        team.setSkills("前端,后端");
        team.setDescription("缺前端");
        Competition competition = competition(1L, "软件赛", NOW.minusDays(1), NOW.plusDays(20), NOW.plusDays(30));
        competition.setTags("前端,后端");
        Map<Long, Set<String>> memberCaps = new LinkedHashMap<>();
        memberCaps.put(1L, Set.of("后端"));
        RecommendationEligibilityService.TeamRule rule =
                service.assessTeam(team, competition, members(1), memberCaps, frontend, new AdvicePreferences(), false);
        assertTrue(rule.recommendable());
        assertTrue(rule.reasons.stream().anyMatch(r -> r.contains("未覆盖")));
        assertEquals("前端（补位）", rule.suggestedRole);
        assertTrue(rule.score > 50);
    }

    @Test
    @DisplayName("本次对话的每周时间覆盖档案值，公开档案不被改写")
    void dialogueWeeklyHoursOverridesProfile() {
        AdvicePreferences preferences = new AdvicePreferences();
        preferences.setWeeklyHours(3);
        Competition competition = competition(1L, "前端赛", NOW.minusDays(1), NOW.plusDays(20), NOW.plusDays(30));
        RecommendationEligibilityService.CompetitionRule rule =
                service.assessCompetition(competition, frontend, preferences, 90);
        assertTrue(rule.reasons.stream().anyMatch(r -> r.contains("每周可投入 3 小时")));
        assertFalse(rule.reasons.stream().anyMatch(r -> r.contains("每周可投入 8 小时")));
    }

    @Test
    @DisplayName("排除方向同样过滤队伍，而不只过滤竞赛")
    void excludesTeamsInExcludedDirection() {
        Team team = team(10L, 1L, "招募中", 1, 3);
        team.setTitle("知衡建模队");
        team.setSkills("建模,文档写作");
        Competition competition = competition(1L, "数学建模竞赛", NOW.minusDays(1), NOW.plusDays(20), NOW.plusDays(30));
        AdvicePreferences preferences = new AdvicePreferences();
        preferences.setExcludedKeywords(new ArrayList<>(List.of("建模")));
        RecommendationEligibilityService.TeamRule rule =
                service.assessTeam(team, competition, members(1), Map.of(), frontend, preferences, false);
        assertEquals(RecommendationEligibilityService.Category.EXCLUDED, rule.category);
        assertTrue(rule.excludeReason.contains("明确排除"));
    }

    @Test
    @DisplayName("不参赛/仅浏览用户仍可看推荐，但会提示不修改状态")
    void notParticipateStillGetsExplorationWithRisk() {
        User user = new User();
        user.setId(8L);
        user.setCompeteStatus("ONLY_VIEW");
        user.setWeeklyHours(5);
        ParticipantProfile profile = ParticipantProfile.of(user, List.of("算法"), List.of());
        Competition competition = competition(1L, "算法赛", NOW.minusDays(1), NOW.plusDays(20), NOW.plusDays(30));
        competition.setTags("算法");
        RecommendationEligibilityService.CompetitionRule rule =
                service.assessCompetition(competition, profile, new AdvicePreferences(), 90);
        assertTrue(rule.recommendable());
        assertTrue(rule.risks.stream().anyMatch(r -> r.contains("不会修改你的公开状态")));
    }

    @Test
    @DisplayName("同一赛事多个报名截止时不擅自择一")
    void multipleDeadlinesBecomeConflict() {
        String text = "校内报名截止 2026年5月10日。全国赛报名截止 2026年6月10日。";
        RuleCompetitionExtractor extractor = new RuleCompetitionExtractor();
        CompetitionExtraction extraction = extractor.extract(text, "关于举办2026年测试大赛的通知");
        assertNull(extraction.getSignupEnd());
        assertFalse(extraction.getConflicts().isEmpty());
        assertTrue(extraction.getConflicts().get(0).contains("多个报名截止"));
    }

    @Test
    @DisplayName("只有月日的报名截止按当日 23:59:59 暂存并标记 DATE")
    void dateOnlyDeadlineKeepsEndOfDayPrecision() {
        String text = "关于举办2026年测试大赛的通知\n主办单位：测试委员会\n报名时间：2026年5月1日至5月20日。";
        CompetitionExtraction extraction = new RuleCompetitionExtractor().extract(text, null);
        assertEquals(LocalDateTime.of(2026, 5, 20, 23, 59, 59), extraction.getSignupEnd());
        assertEquals("DATE", extraction.getSignupEndPrecision());
        assertTrue(extraction.getDateNote().contains("23:59:59"));
        assertEquals(LocalDateTime.of(2026, 5, 1, 0, 0), extraction.getSignupStart());
        assertEquals("测试委员会", extraction.getOrganizer());
    }

    // ==================== 返修回归：报告 R6 ====================

    @Test
    @DisplayName("缺少报名开始时间时不能只凭未来截止判为可报名")
    void missingSignupStartNeedsConfirm() {
        Competition competition = competition(1L, "开始未知赛", null, NOW.plusDays(40), NOW.plusDays(60));
        RecommendationEligibilityService.CompetitionRule rule =
                service.assessCompetition(competition, frontend, new AdvicePreferences(), 90);
        assertEquals(RecommendationEligibilityService.Category.NEEDS_CONFIRM, rule.category);
        assertTrue(rule.risks.stream().anyMatch(r -> r.contains("无法确认当前是否已开放")));
    }

    @Test
    @DisplayName("正文明确写明“即日起报名”时才可作为已开放的依据，并保留出处")
    void explicitOpenNowTextAllowsOpen() {
        Competition competition = competition(1L, "即日起报名赛", null, NOW.plusDays(40), NOW.plusDays(60));
        competition.setDescription("报名方式：即日起报名，截止 2026年11月30日。");
        RecommendationEligibilityService.CompetitionRule rule =
                service.assessCompetition(competition, frontend, new AdvicePreferences(), 90);
        assertEquals(RecommendationEligibilityService.Category.OPEN, rule.category);
        assertTrue(rule.reasons.stream().anyMatch(r -> r.contains("即日起报名")));
        assertTrue(rule.risks.stream().anyMatch(r -> r.contains("报名开始时间未录入")));
    }

    @Test
    @DisplayName("开始与截止都缺失时仍归为需确认，不当作无限期")
    void bothDatesMissingNeedsConfirm() {
        Competition competition = competition(1L, "无日期赛", null, null, null);
        RecommendationEligibilityService.CompetitionRule rule =
                service.assessCompetition(competition, frontend, new AdvicePreferences(), 90);
        assertEquals(RecommendationEligibilityService.Category.NEEDS_CONFIRM, rule.category);
        assertTrue(rule.risks.stream().anyMatch(r -> r.contains("均未公布")));
    }

    @Test
    @DisplayName("完整报名区间覆盖当前时刻才算可报名")
    void completeRangeCoveringNowIsOpen() {
        Competition competition = competition(1L, "区间赛", NOW.minusDays(5), NOW.plusDays(20), NOW.plusDays(40));
        RecommendationEligibilityService.CompetitionRule rule =
                service.assessCompetition(competition, frontend, new AdvicePreferences(), 90);
        assertEquals(RecommendationEligibilityService.Category.OPEN, rule.category);
        assertEquals("查看赛事详情并核对参赛条件", rule.nextStep);
    }

    private Competition competition(Long id, String name, LocalDateTime start, LocalDateTime end,
                                    LocalDateTime eventStart) {
        Competition competition = new Competition();
        competition.setId(id);
        competition.setName(name);
        competition.setOrganizer("测试主办方");
        competition.setLevel("校级");
        competition.setSignupStart(start);
        competition.setSignupEnd(end);
        competition.setStartTime(eventStart);
        competition.setDescription("测试赛事");
        competition.setStatus("报名中");
        return competition;
    }

    private Team team(Long id, Long captainId, String status, int memberCount, int max) {
        Team team = new Team();
        team.setId(id);
        team.setCaptainId(captainId);
        team.setTitle("测试队伍");
        team.setDescription("测试队伍简介");
        team.setStatus(status);
        team.setMemberCount(memberCount);
        team.setMaxMembers(max);
        team.setCompetitionId(1L);
        team.setDeadline(NOW.plusDays(20));
        team.setSkills("");
        return team;
    }

    private List<TeamMember> members(int count) {
        List<TeamMember> members = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            TeamMember member = new TeamMember();
            member.setTeamId(10L);
            member.setUserId((long) i);
            members.add(member);
        }
        return members;
    }
}
