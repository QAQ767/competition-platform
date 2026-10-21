package com.campus.competition.service;

import com.campus.competition.advice.AdvicePreferences;
import com.campus.competition.advice.ParticipantProfile;
import com.campus.competition.config.LlmProperties;
import com.campus.competition.entity.Competition;
import com.campus.competition.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** 独立验收 R1—R3/R5 原始输入及根因回归；不调用外网。 */
class AdviceRepairTest {
    private AdvicePreferenceService service;
    private LlmService llm;
    private LlmProperties config;
    private ParticipantProfile profile;
    private List<Competition> catalog;

    @BeforeEach void setup() {
        llm = mock(LlmService.class); config = new LlmProperties();
        service = new AdvicePreferenceService(llm, config, new ObjectMapper());
        User user = new User(); user.setId(7L); user.setWeeklyHours(5);
        profile = ParticipantProfile.of(user,List.of("Java"),List.of());
        catalog = List.of(competition(1L,"蓝桥杯"), competition(2L,"蓝桥杯软件赛（2026演示）"),
                competition(3L,"中国大学生计算机设计大赛"), competition(4L,"中国大学生广告艺术设计大赛"),
                competition(5L,"全国大学生数学建模竞赛"));
    }
    private Competition competition(Long id, String name) { Competition c = new Competition(); c.setId(id); c.setName(name); return c; }
    private AdvicePreferenceService.Applied chat(String text, AdvicePreferences current) { return service.applyChat(text,current,profile,catalog); }

    @Test void fullNonCupNameResolvesOnlyToItsRealIdentity() {
        var result = chat("我想参加中国大学生计算机设计大赛，推荐相关队伍", new AdvicePreferences());
        assertEquals(List.of("中国大学生计算机设计大赛"), result.preferences.getCompetitionKeywords());
        assertEquals(List.of(3L), result.preferences.getResolvedCompetitionIds());
        assertEquals("RESOLVED", result.preferences.getCompetitionIntentStatus());
        assertEquals("TEAMS", result.preferences.getOutputTarget()); assertFalse(result.clarificationRequired);
    }
    @Test void absentFullNameIsRetainedAndMustClarify() {
        var result = service.applyChat("我想参加中国大学生计算机设计大赛，推荐相关队伍",new AdvicePreferences(),profile,List.of(competition(1L,"蓝桥杯")));
        assertEquals("UNRESOLVED",result.preferences.getCompetitionIntentStatus());
        assertTrue(result.preferences.getResolvedCompetitionIds().isEmpty());
        assertTrue(result.clarificationRequired); assertTrue(result.questions.get(0).contains("中国大学生计算机设计大赛"));
        var again = service.applyChat("换一些",result.preferences,profile,List.of(competition(1L,"蓝桥杯")));
        assertTrue(again.clarificationRequired); assertFalse(again.preferences.getCompetitionKeywords().isEmpty());
    }
    @Test void fullNameWithoutParticipationVerbIsNotLost() {
        var result = service.applyChat("中国大学生计算机设计大赛相关队伍",new AdvicePreferences(),profile,List.of());
        assertTrue(result.clarificationRequired); assertEquals("UNRESOLVED",result.preferences.getCompetitionIntentStatus());
    }
    @Test void ambiguousShortNameAsksAndFullNameThenResolves() {
        var shortName = chat("我想参加设计大赛，推荐相关队伍",new AdvicePreferences());
        assertEquals("AMBIGUOUS",shortName.preferences.getCompetitionIntentStatus()); assertTrue(shortName.clarificationRequired);
        var selected = chat("中国大学生计算机设计大赛",shortName.preferences);
        assertEquals("RESOLVED",selected.preferences.getCompetitionIntentStatus());assertEquals(List.of(3L),selected.preferences.getResolvedCompetitionIds());
    }
    @Test void nonexistentCupNeverBecomesUnconstrained() {
        var result = chat("我想参加不存在的银河杯，只推荐队伍",new AdvicePreferences());
        assertEquals("UNRESOLVED",result.preferences.getCompetitionIntentStatus()); assertTrue(result.clarificationRequired);
    }
    @Test void cupFamilyAllowsItsPlatformInstancesAndUnrelatedNamesAreExcluded() {
        var result = chat("我想参加蓝桥杯，只推荐队伍",new AdvicePreferences());
        assertEquals(List.of(1L,2L),result.preferences.getResolvedCompetitionIds());
        var clear = chat("取消赛事限制",result.preferences);
        assertEquals("NONE",clear.preferences.getCompetitionIntentStatus()); assertTrue(clear.preferences.getResolvedCompetitionIds().isEmpty());
    }
    @Test void chineseNumberActuallyReplacesPreviousHours() {
        var start = chat("每周5小时，推荐比赛和队伍",new AdvicePreferences());
        var next = chat("每周只有三小时，推荐适合我的比赛和队伍",start.preferences);
        assertEquals(3,next.preferences.getWeeklyHours());
        assertEquals(12,chat("每周十二小时",next.preferences).preferences.getWeeklyHours());
    }
    @Test void validatedSemanticChangeSurvivesWhenRulesHaveNoNewValue() {
        config.setProvider("ollama");
        when(llm.generateStructured(anyString(),anyString(),anyInt())).thenReturn(LlmService.LlmResult.ok("{\"reply\":\"时间已改为4小时\",\"changes\":[{\"field\":\"weeklyHours\",\"op\":\"SET\",\"values\":[\"4\"]}],\"questions\":[]}"));
        AdvicePreferences old = new AdvicePreferences();old.setWeeklyHours(5);
        var result=chat("时间改成一个下午左右，推荐比赛",old);
        assertEquals(4,result.preferences.getWeeklyHours());assertEquals("LLM",result.mode);
    }
    @Test void explicitRuleChangeStillCorrectsIncorrectModelValue() {
        config.setProvider("ollama");
        when(llm.generateStructured(anyString(),anyString(),anyInt())).thenReturn(LlmService.LlmResult.ok("{\"reply\":\"x\",\"changes\":[{\"field\":\"weeklyHours\",\"op\":\"SET\",\"values\":[\"5\"]}],\"questions\":[]}"));
        assertEquals(3,chat("每周只有三小时",new AdvicePreferences()).preferences.getWeeklyHours());
    }
    @Test void reopeningMathRemovesExclusionWithoutInventingSpecificCompetition() {
        var old = chat("推荐比赛，不要建模",new AdvicePreferences());assertTrue(old.preferences.getExcludedKeywords().contains("建模"));
        var next = chat("现在也可以参加数学建模了，推荐比赛和队伍",old.preferences);
        assertFalse(next.preferences.getExcludedKeywords().contains("建模"));assertTrue(next.preferences.getCompetitionKeywords().isEmpty());assertFalse(next.clarificationRequired);
    }
    @Test void cancellingTimeIsExplicitInsteadOfFallingBackToProfile() {
        var old=chat("每周五小时，最近一个月",new AdvicePreferences());
        var clear=chat("取消时间限制",old.preferences);
        assertNull(clear.preferences.getWeeklyHours());assertNull(clear.preferences.getWindowDays());
        assertTrue(clear.preferences.isWeeklyHoursUnrestricted());assertTrue(clear.preferences.isWindowUnrestricted());
        var set=chat("每周三小时，最近三十天",clear.preferences);
        assertEquals(3,set.preferences.getWeeklyHours());assertEquals(30,set.preferences.getWindowDays());
        assertFalse(set.preferences.isWeeklyHoursUnrestricted());assertFalse(set.preferences.isWindowUnrestricted());
    }
    @Test void targetSwitchPreservesCompetitionIdentity() {
        var teams=chat("我想参加蓝桥杯，只推荐队伍",new AdvicePreferences());
        var comps=chat("现在不找队伍了，只推荐比赛",teams.preferences);
        assertEquals("COMPETITIONS",comps.preferences.getOutputTarget());assertFalse(comps.preferences.getTeamOnly());
        assertEquals(teams.preferences.getResolvedCompetitionIds(),comps.preferences.getResolvedCompetitionIds());
        assertEquals("BOTH",chat("也看看比赛",teams.preferences).preferences.getOutputTarget());
        assertEquals("COMPETITIONS",chat("不找队伍",teams.preferences).preferences.getOutputTarget());
        assertEquals("COMPETITIONS",chat("只看比赛",teams.preferences).preferences.getOutputTarget());
        assertEquals("BOTH",chat("比赛和队伍都要",comps.preferences).preferences.getOutputTarget());
    }
    @Test void conflictingIdentityAndExclusionAskInsteadOfEmptySuccess() {
        var result=chat("我想参加全国大学生数学建模竞赛，但不要建模",new AdvicePreferences());
        assertTrue(result.clarificationRequired);assertTrue(result.questions.get(0).contains("冲突"));
    }
    @Test void oldBooleanSessionsAndNewTargetsRoundTrip() throws Exception {
        ObjectMapper mapper=new ObjectMapper();
        assertEquals("TEAMS",mapper.readValue("{\"teamOnly\":true}",AdvicePreferences.class).getOutputTarget());
        var p=new AdvicePreferences();p.setOutputTarget("COMPETITIONS");
        assertEquals("COMPETITIONS",mapper.readValue(mapper.writeValueAsString(p),AdvicePreferences.class).getOutputTarget());
        assertEquals("COMPETITIONS",p.copy().getOutputTarget());
    }

    @Test void generalConditionsNeverInventAnEventInNewSessions() {
        for (String text : List.of("帮我推荐几个适合新手的比赛", "推荐算法类比赛",
                "给我找几场适合入门的竞赛", "推荐一些算法相关赛事", "推荐算法竞赛",
                "我会Java，每周三小时，帮我推荐几场适合入门的算法竞赛",
                "给我推荐适宜零基础参加的挑战赛", "推荐前端方向的比赛",
                "推荐容易上手的大赛", "推荐一些“算法”相关赛事", "推荐“算法”类比赛")) {
            var result = chat(text, new AdvicePreferences());
            assertEquals(List.of(), result.preferences.getCompetitionKeywords(), text);
            assertEquals("NONE", result.preferences.getCompetitionIntentStatus(), text);
            assertTrue(result.preferences.getResolvedCompetitionIds().isEmpty(), text);
            assertFalse(result.clarificationRequired, text);
        }
        var easy = chat("帮我推荐几个适合新手的比赛", new AdvicePreferences());
        assertEquals("入门", easy.preferences.getDifficulty());
        assertTrue(chat("推荐算法类比赛", new AdvicePreferences()).preferences.getDirections().contains("算法"));
    }

    @Test void modelProposedConditionCannotBecomeAnEventEvenWhenGroundedInUserText() {
        catalog = List.of(competition(9L, "Java算法挑战赛"));
        config.setProvider("ollama");
        for (String name : List.of("几个适合新手", "算法类比赛", "算法", "Java")) {
            when(llm.generateStructured(anyString(),anyString(),anyInt())).thenReturn(LlmService.LlmResult.ok(
                    "{\"reply\":\"x\",\"changes\":[{\"field\":\"competitionKeywords\",\"op\":\"SET\",\"values\":[\"" + name + "\"]}],\"questions\":[]}"));
            var result = chat("我会Java，帮我推荐几个适合新手的算法类比赛", new AdvicePreferences());
            assertTrue(result.preferences.getCompetitionKeywords().isEmpty(), name);
            assertEquals("NONE", result.preferences.getCompetitionIntentStatus(), name);
            assertFalse(result.clarificationRequired, name);
        }
    }

    @Test void directionInsideCatalogTitleIsNotAnImplicitShortEventName() {
        catalog = List.of(competition(9L, "Java算法挑战赛"));
        for (String text : List.of("推荐算法", "推荐Java", "帮我推荐算法类比赛")) {
            var result = chat(text, new AdvicePreferences());
            assertTrue(result.preferences.getCompetitionKeywords().isEmpty(), text);
            assertEquals("NONE", result.preferences.getCompetitionIntentStatus(), text);
            assertFalse(result.clarificationRequired, text);
        }
    }

    @Test void generalFollowUpPreservesRealIdentityEvenIfModelReplacesItWithCondition() {
        var selected = chat("我想参加蓝桥杯，只推荐队伍", new AdvicePreferences()).preferences;
        config.setProvider("ollama");
        when(llm.generateStructured(anyString(),anyString(),anyInt())).thenReturn(LlmService.LlmResult.ok(
                "{\"reply\":\"x\",\"changes\":[{\"field\":\"competitionKeywords\",\"op\":\"SET\",\"values\":[\"算法类比赛\"]}],\"questions\":[]}"));
        var result = chat("每周三小时，推荐算法类比赛，适合新手", selected);
        assertEquals(selected.getCompetitionKeywords(), result.preferences.getCompetitionKeywords());
        assertEquals(selected.getResolvedCompetitionIds(), result.preferences.getResolvedCompetitionIds());
        assertEquals("RESOLVED", result.preferences.getCompetitionIntentStatus());
        assertFalse(result.clarificationRequired); assertEquals(3, result.preferences.getWeeklyHours());
        assertEquals("入门", result.preferences.getDifficulty());
    }

    @Test void generalFollowUpDoesNotSilentlyClearUnknownNamedEvent() {
        var unknown = chat("我想参加银河杯，推荐相关队伍", new AdvicePreferences()).preferences;
        var result = chat("给我找几场适合入门的竞赛", unknown);
        assertEquals(unknown.getCompetitionKeywords(), result.preferences.getCompetitionKeywords());
        assertEquals("UNRESOLVED", result.preferences.getCompetitionIntentStatus());
        assertTrue(result.clarificationRequired);
    }
}
