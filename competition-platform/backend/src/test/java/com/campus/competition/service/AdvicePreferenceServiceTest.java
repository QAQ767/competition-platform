package com.campus.competition.service;

import com.campus.competition.advice.AdvicePreferences;
import com.campus.competition.advice.ParticipantProfile;
import com.campus.competition.config.LlmProperties;
import com.campus.competition.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 需求提取只认用户明确写出的内容；模型输出不合格时整轮回退规则。 */
class AdvicePreferenceServiceTest {

    private LlmService llmService;
    private LlmProperties properties;
    private AdvicePreferenceService service;
    private ParticipantProfile profile;

    @BeforeEach
    void setUp() {
        llmService = mock(LlmService.class);
        properties = new LlmProperties();
        service = new AdvicePreferenceService(llmService, properties, new ObjectMapper());
        User user = new User();
        user.setId(7L);
        user.setMajor("软件工程");
        user.setWeeklyHours(0);
        user.setCompeteStatus("WANT_INVITE");
        profile = ParticipantProfile.of(user, new ArrayList<>(), new ArrayList<>());
    }

    @Test
    @DisplayName("规则提取时间、技能、角色和排除项，不编造经历")
    void extractsExplicitFacts() {
        AdvicePreferenceService.Applied applied = service.apply(
                "我是软件工程专业，会 Vue，做过预约系统，每周大概 6 小时，想找一个缺前端的队伍，不参加硬件比赛。",
                new AdvicePreferences(), profile);
        assertEquals("RULE", applied.mode);
        assertEquals(6, applied.preferences.getWeeklyHours());
        assertTrue(applied.preferences.getSkills().contains("前端"));
        assertTrue(applied.preferences.getRoles().contains("前端"));
        assertTrue(applied.preferences.getExcludedKeywords().contains("硬件"));
        assertFalse(applied.preferences.getSkills().contains("预约系统"));
        assertTrue(applied.preferences.sourceOf("weeklyHours").equals("DIALOGUE"));
    }

    @Test
    @DisplayName("后续消息覆盖每周时间，不写回公开档案")
    void laterMessageOverridesHours() {
        AdvicePreferences current = new AdvicePreferences();
        current.setWeeklyHours(6);
        current.markSource("weeklyHours", AdvicePreferences.SOURCE_DIALOGUE);
        AdvicePreferenceService.Applied applied = service.apply("我只能投入 3 小时", current, profile);
        assertEquals(3, applied.preferences.getWeeklyHours());
        assertEquals(3, applied.changes.stream()
                .filter(c -> "weeklyHours".equals(c.getField()))
                .findFirst().orElseThrow().getValues().stream().findFirst().map(Integer::parseInt).orElse(0));
    }

    @Test
    @DisplayName("“不想参加建模了”会移除方向并加入排除项")
    void negationRemovesPreviousDirection() {
        AdvicePreferences current = new AdvicePreferences();
        current.setDirections(new ArrayList<>(List.of("建模", "数据分析")));
        current.markSource("directions", AdvicePreferences.SOURCE_DIALOGUE);
        AdvicePreferenceService.Applied applied = service.apply("不想参加建模了", current, profile);
        assertFalse(applied.preferences.getDirections().contains("建模"));
        assertTrue(applied.preferences.getExcludedKeywords().contains("建模"));
        assertTrue(applied.preferences.getDirections().contains("数据分析"));
    }

    @Test
    @DisplayName("只提到专业时不会编造技能或每周时间")
    void doesNotInventSkillsOrHours() {
        AdvicePreferenceService.Applied applied = service.apply("我是软件工程专业", new AdvicePreferences(), profile);
        assertTrue(applied.changes.isEmpty());
        assertTrue(applied.preferences.getSkills().isEmpty());
        assertEquals(null, applied.preferences.getWeeklyHours());
        assertFalse(applied.questions.isEmpty());
        assertTrue(applied.questions.size() <= 2);
    }

    @Test
    @DisplayName("0 小时不会被当成确定的可用时间")
    void zeroHoursIsAskedBack() {
        AdvicePreferenceService.Applied applied = service.apply("我每周 0 小时", new AdvicePreferences(), profile);
        assertEquals(null, applied.preferences.getWeeklyHours());
        assertTrue(applied.questions.stream().anyMatch(q -> q.contains("0 小时")));
    }

    @Test
    @DisplayName("模型输出不合格时整批回退规则并说明原因")
    void invalidModelOutputFallsBackToRules() {
        properties.setProvider("ollama");
        properties.setBaseUrl("http://localhost:11434");
        properties.setModel("deepseek-r1:7b");
        when(llmService.generateStructured(anyString(), anyString(), anyInt()))
                .thenReturn(LlmService.LlmResult.ok("这不是 JSON"));
        AdvicePreferenceService.Applied applied = service.apply("每周 3 小时", new AdvicePreferences(), profile);
        assertEquals("RULE", applied.mode);
        assertTrue(applied.fallbackReason.contains("未通过校验"));
        assertEquals(3, applied.preferences.getWeeklyHours());
    }

    @Test
    @DisplayName("合法模型输出被采纳并标记为 LLM")
    void validModelOutputIsApplied() {
        properties.setProvider("ollama");
        properties.setBaseUrl("http://localhost:11434");
        properties.setModel("deepseek-r1:7b");
        String json = "{\"reply\":\"已记录\",\"changes\":["
                + "{\"field\":\"weeklyHours\",\"op\":\"SET\",\"values\":[\"4\"]},"
                + "{\"field\":\"roles\",\"op\":\"ADD\",\"values\":[\"后端\"]}],"
                + "\"questions\":[]}";
        when(llmService.generateStructured(anyString(), anyString(), anyInt()))
                .thenReturn(LlmService.LlmResult.ok(json));
        AdvicePreferenceService.Applied applied = service.apply("我每周 4 小时，想做后端", new AdvicePreferences(), profile);
        assertEquals("LLM", applied.mode);
        assertEquals(4, applied.preferences.getWeeklyHours());
        assertTrue(applied.preferences.getRoles().contains("后端"));
        assertTrue(applied.questions.size() <= 2);
    }

    @Test
    @DisplayName("模型给假字段、越界时间或超长文本时拒绝整批结果")
    void invalidModelFieldsAreRejected() {
        properties.setProvider("ollama");
        properties.setBaseUrl("http://localhost:11434");
        properties.setModel("deepseek-r1:7b");
        when(llmService.generateStructured(anyString(), anyString(), anyInt()))
                .thenReturn(LlmService.LlmResult.ok(
                        "{\"reply\":\"x\",\"changes\":[{\"field\":\"admin\",\"op\":\"SET\",\"values\":[\"true\"]}],\"questions\":[]}"));
        AdvicePreferenceService.Applied applied = service.apply("随便说点什么", new AdvicePreferences(), profile);
        assertEquals("RULE", applied.mode);
    }
}
