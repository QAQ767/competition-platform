package com.campus.competition.service;

import com.campus.competition.advice.AdvicePreferences;
import com.campus.competition.advice.ParticipantProfile;
import com.campus.competition.config.LlmProperties;
import com.campus.competition.entity.Competition;
import com.campus.competition.entity.User;
import com.campus.competition.mapper.CompetitionMapper;
import com.campus.competition.mapper.SkillTagMapper;
import com.campus.competition.mapper.TeamApplicationMapper;
import com.campus.competition.mapper.TeamMapper;
import com.campus.competition.mapper.TeamMemberMapper;
import com.campus.competition.mapper.UserMapper;
import com.campus.competition.mapper.UserSkillMapper;
import com.campus.competition.vo.StudentRecommendationVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** “只看队伍”必须真的不返回竞赛，而不是只给队伍加分。 */
class StudentRecommendationServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 20, 12, 0);

    private StudentRecommendationService service;
    private ParticipantProfile profile;
    private CompetitionMapper competitionMapper;
    private LlmService llm;
    private LlmProperties llmProperties;
    private TeamMapper teamMapper;
    private TeamMemberMapper teamMembers;

    @BeforeEach
    void setUp() {
        competitionMapper = mock(CompetitionMapper.class);
        Competition competition = new Competition();
        competition.setId(1L);
        competition.setName("前端挑战赛");
        competition.setOrganizer("测试主办方");
        competition.setLevel("校级");
        competition.setTags("前端");
        competition.setSignupStart(NOW.minusDays(2));
        competition.setSignupEnd(NOW.plusDays(20));
        competition.setStartTime(NOW.plusDays(30));
        competition.setDescription("前端方向赛事");
        competition.setEligibilityText("高校学生");

        when(competitionMapper.selectList(any())).thenReturn(List.of(competition));
        when(competitionMapper.selectById(1L)).thenReturn(competition);

        RecommendationEligibilityService eligibility =
                new RecommendationEligibilityService(Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE));
        llmProperties = new LlmProperties(); // 未配置 → 规则模式，不触网
        llm = mock(LlmService.class);

        teamMapper = mock(TeamMapper.class); teamMembers = mock(TeamMemberMapper.class);
        service = new StudentRecommendationService(competitionMapper, teamMapper,
                teamMembers, mock(TeamApplicationMapper.class),
                mock(UserMapper.class), mock(UserSkillMapper.class), mock(SkillTagMapper.class),
                eligibility, llm, llmProperties, new ObjectMapper());

        User user = new User();
        user.setId(7L);
        user.setNickname("前端同学");
        user.setMajor("软件工程");
        user.setWeeklyHours(8);
        user.setCompeteStatus("WANT_INVITE");
        user.setIntro("喜欢 Vue");
        profile = ParticipantProfile.of(user, List.of("前端"), List.of());
    }

    private void eligibleTeam() {
        var team = new com.campus.competition.entity.Team(); team.setId(20L); team.setTitle("前端队伍");
        team.setCaptainId(99L); team.setCompetitionId(1L); team.setStatus("招募中");
        team.setMaxMembers(4); team.setMemberCount(1); team.setDeadline(NOW.plusDays(5));
        var member = new com.campus.competition.entity.TeamMember(); member.setTeamId(20L); member.setUserId(99L);
        when(teamMapper.selectList(any())).thenReturn(List.of(team)); when(teamMapper.selectById(20L)).thenReturn(team);
        when(teamMembers.selectList(any())).thenReturn(List.of(member));
    }
    @Test void competitionOnlyActuallyHidesOtherwiseEligibleTeam() {
        eligibleTeam();
        assertEquals(1, service.recommend(profile,new AdvicePreferences(),90).getTeams().size());
        var preferences = new AdvicePreferences(); preferences.setOutputTarget("COMPETITIONS");
        var result = service.recommend(profile,preferences,90);
        assertEquals(1,result.getCompetitions().size()); assertTrue(result.getTeams().isEmpty());
    }
    @Test void teamOnlyStillChecksAssociatedCompetitionIdentity() {
        eligibleTeam();
        var p = new AdvicePreferences(); p.setOutputTarget("TEAMS");p.setCompetitionKeywords(List.of("前端挑战赛"));
        p.setCompetitionIntentStatus("RESOLVED");p.setResolvedCompetitionIds(List.of(1L));
        assertEquals(1,service.recommend(profile,p,90).getTeams().size());
        p.setResolvedCompetitionIds(List.of(999L));
        assertTrue(service.recommend(profile,p,90).getTeams().isEmpty());
    }
    @Test void unresolvedIdentityCannotFallThroughToGeneralRecommendations() {
        eligibleTeam();
        var p = new AdvicePreferences();p.setCompetitionKeywords(List.of("前端挑战赛"));p.setCompetitionIntentStatus("UNRESOLVED");
        var result = service.recommend(profile,p,90);
        assertTrue(result.getCompetitions().isEmpty());assertTrue(result.getTeams().isEmpty());
    }

    @Test void generalRequestStillProducesRealCardsAndFollowUpKeepsNamedCompetition() {
        eligibleTeam();
        var interpreter = new AdvicePreferenceService(llm, llmProperties, new ObjectMapper());
        for (String text : List.of("帮我推荐几个适合新手的比赛", "推荐算法类比赛",
                "给我找几场适合入门的竞赛", "推荐一些算法相关赛事",
                "我会Java，每周三小时，推荐一些算法相关赛事")) {
            var applied = interpreter.applyChat(text, new AdvicePreferences(), profile, service.competitionCatalog());
            assertEquals("NONE", applied.preferences.getCompetitionIntentStatus(), text);
            assertTrue(applied.preferences.getCompetitionKeywords().isEmpty(), text);
            assertTrue(!applied.clarificationRequired, text);
            var cards = service.recommend(profile, applied.preferences, 90);
            assertEquals(1, cards.getCompetitions().size(), text);
            assertEquals(1L, cards.getCompetitions().get(0).getId(), text);
        }
        var named = interpreter.applyChat("我想参加前端挑战赛", new AdvicePreferences(), profile, service.competitionCatalog());
        var follow = interpreter.applyChat("推荐一些算法相关赛事", named.preferences, profile, service.competitionCatalog());
        assertEquals(List.of(1L), follow.preferences.getResolvedCompetitionIds());
        assertEquals("RESOLVED", follow.preferences.getCompetitionIntentStatus());
        assertEquals(1L, service.recommend(profile, follow.preferences, 90).getCompetitions().get(0).getId());
    }

    @Test void validationRetryIsBoundedAndRuleFieldsStayClean() {
        llmProperties.setProvider("ollama"); llmProperties.setTimeoutSeconds(10);
        when(llm.generateStructured(any(), any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(LlmService.LlmResult.ok("{\"insight\":\"x\",\"competitions\":[{\"id\":999}],\"teams\":[]}"));
        var result = service.recommend(profile, new AdvicePreferences(), 90);
        assertEquals("RULE", result.getMode()); assertEquals(1, result.getRetryCount());
        assertEquals("VALIDATION_FAILED", result.getFailureCategory());
        org.mockito.Mockito.verify(llm, org.mockito.Mockito.times(2)).generateStructured(any(), any(), org.mockito.ArgumentMatchers.anyInt());
        assertTrue(result.getCompetitions().get(0).getReason().length() > 1);
    }
    @Test void changedEligibilityRemovesCardAndOldInsight() {
        llmProperties.setProvider("ollama");
        when(llm.generateStructured(any(), any(), org.mockito.ArgumentMatchers.anyInt())).thenAnswer(inv -> {
            when(competitionMapper.selectById(1L)).thenReturn(null);
            return LlmService.LlmResult.ok("{\"insight\":\"该赛事可报名\",\"competitions\":[{\"id\":1,\"score\":70,\"reason\":\"方向相符\",\"risks\":[],\"nextStep\":\"核对公告\"}],\"teams\":[]}");
        });
        var result = service.recommend(profile, new AdvicePreferences(), 90);
        assertTrue(result.getCompetitions().isEmpty()); org.junit.jupiter.api.Assertions.assertNull(result.getInsight());
    }

    @Test
    @DisplayName("未勾选只看队伍时正常返回竞赛")
    void returnsCompetitionsByDefault() {
        StudentRecommendationVO vo = service.recommend(profile, new AdvicePreferences(), 90);
        assertEquals(1, vo.getCompetitions().size());
        assertEquals("前端挑战赛", vo.getCompetitions().get(0).getName());
        assertEquals("RULE", vo.getMode());
    }

    @Test
    @DisplayName("勾选只看队伍后不返回竞赛，并说明原因")
    void teamOnlyHidesCompetitions() {
        AdvicePreferences preferences = new AdvicePreferences();
        preferences.setTeamOnly(true);
        StudentRecommendationVO vo = service.recommend(profile, preferences, 90);

        assertTrue(vo.getCompetitions().isEmpty(), "只看队伍时不能返回竞赛推荐");
        assertTrue(vo.getNotes().stream().anyMatch(n -> n.contains("只看队伍")),
                "必须说明本次不返回竞赛：" + vo.getNotes());
        assertTrue(vo.getNotes().stream().noneMatch(n -> n.contains("没有符合条件的竞赛")),
                "不能把“主动只看队伍”说成“没有符合条件的竞赛”");
    }
}
