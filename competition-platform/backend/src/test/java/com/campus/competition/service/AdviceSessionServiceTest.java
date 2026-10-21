package com.campus.competition.service;

import com.campus.competition.advice.AdvicePreferences;
import com.campus.competition.common.BusinessException;
import com.campus.competition.config.AdviceProperties;
import com.campus.competition.entity.User;
import com.campus.competition.mapper.AchievementMapper;
import com.campus.competition.mapper.SkillTagMapper;
import com.campus.competition.mapper.UserMapper;
import com.campus.competition.mapper.UserSkillMapper;
import com.campus.competition.vo.AdviceMessageResultVO;
import com.campus.competition.vo.AdviceSessionVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 会话归属、版本号、长度限制与存储故障都必须有明确结果。 */
class AdviceSessionServiceTest {

    private InMemoryStore store;
    private AdviceSessionService service;
    private UserMapper userMapper;
    private StudentRecommendationService recommendations;

    @BeforeEach
    void setUp() {
        store = new InMemoryStore();
        userMapper = mock(UserMapper.class);
        User user = new User();
        user.setId(1L);
        user.setNickname("小林");
        user.setMajor("软件工程");
        user.setWeeklyHours(6);
        user.setCompeteStatus("WANT_INVITE");
        when(userMapper.selectById(1L)).thenReturn(user);
        User other = new User();
        other.setId(2L);
        when(userMapper.selectById(2L)).thenReturn(other);

        AdviceProperties properties = new AdviceProperties();
        AdvicePreferenceService preferenceService = new AdvicePreferenceService(
                mock(LlmService.class), new com.campus.competition.config.LlmProperties(), new ObjectMapper().findAndRegisterModules());
        recommendations = mock(StudentRecommendationService.class);
        when(recommendations.recommend(any(), any(), org.mockito.ArgumentMatchers.anyInt())).thenAnswer(inv -> {
            var result = new com.campus.competition.vo.StudentRecommendationVO(); result.setMode("RULE"); result.setFallbackReason("模拟规则");
            var team = new com.campus.competition.vo.TeamRecommendationVO(); team.setId(14L); team.setTitle("真实队伍"); team.setReason("每周时间匹配"); result.getTeams().add(team); return result;
        });
        service = new AdviceSessionService(store, preferenceService, recommendations,
                properties, new ObjectMapper().findAndRegisterModules(), userMapper, mock(UserSkillMapper.class),
                mock(SkillTagMapper.class), mock(AchievementMapper.class));
    }

    @Test void chatReturnsTrustedCardsInOneVersionAndPersistsThem() {
        var session = service.create(1L);
        var result = service.chat(1L, session.getId(), 0, "我会 Java，每周 5 小时，推荐竞赛和队伍");
        assertEquals(1, result.getVersion()); assertEquals(5, result.getPreferences().getWeeklyHours());
        assertEquals(14L, result.getRecommendation().getTeams().get(0).getId());
        assertEquals(14L, service.get(1L, session.getId()).getMessages().get(1).getRecommendation().getTeams().get(0).getId());
        org.mockito.Mockito.verify(userMapper, org.mockito.Mockito.never()).updateById(any());
    }
    @Test void greetingsAndExplanationsNeverCallRecommendation() {
        var session = service.create(1L);
        service.chat(1L, session.getId(), 0, "你好"); service.chat(1L, session.getId(), 1, "为什么推荐这个");
        org.mockito.Mockito.verifyNoInteractions(recommendations);
    }
    @Test void ordinaryConversationDoesNotGenerateCards() {
        var session = service.create(1L);
        var result = service.chat(1L, session.getId(), 0, "你可以做些什么");
        org.junit.jupiter.api.Assertions.assertNull(result.getRecommendation());
        org.mockito.Mockito.verifyNoInteractions(recommendations);
    }
    @Test void unresolvedCompetitionAsksInsteadOfClaimingUnrelatedRecommendations() {
        var session = service.create(1L);
        var result = service.chat(1L, session.getId(), 0, "我想参加中国大学生计算机设计大赛，推荐相关队伍");
        org.junit.jupiter.api.Assertions.assertNull(result.getRecommendation());
        assertEquals("UNRESOLVED",result.getPreferences().getCompetitionIntentStatus());
        assertTrue(result.getReply().contains("平台暂未找到")); assertTrue(!result.getQuestions().isEmpty());
        org.mockito.Mockito.verify(recommendations,org.mockito.Mockito.never()).recommend(any(),any(),org.mockito.ArgumentMatchers.anyInt());
    }
    @Test void chatRequiresVersionAndRejectsOtherOwner() {
        var session = service.create(1L);
        assertEquals(409, assertThrows(BusinessException.class, () -> service.chat(1L, session.getId(), null, "推荐")).getCode());
        assertEquals(403, assertThrows(BusinessException.class, () -> service.chat(2L, session.getId(), 0, "推荐")).getCode());
    }
    @Test void explicitIdentityAndContinuousEditsRemainInSession() {
        var session = service.create(1L); service.chat(1L, session.getId(), 0, "我想参加蓝桥杯，只推荐队伍");
        service.chat(1L, session.getId(), 1, "不要建模"); service.chat(1L, session.getId(), 2, "只有 3 小时"); service.chat(1L, session.getId(), 3, "最近一个月");
        var p = service.get(1L, session.getId()).getPreferences();
        assertEquals(java.util.List.of("蓝桥杯"), p.getCompetitionKeywords()); assertEquals(true, p.getTeamOnly());
        assertEquals(3, p.getWeeklyHours()); assertEquals(30, p.getWindowDays()); assertTrue(p.getExcludedKeywords().contains("建模"));
    }
    @Test void changingResultsExcludesLastTrustedIds() {
        var session = service.create(1L); service.chat(1L, session.getId(), 0, "推荐"); service.chat(1L, session.getId(), 1, "换一些");
        assertTrue(service.get(1L, session.getId()).getPreferences().getExcludedTeamIds().contains(14L));
    }
    @Test void deleteDuringGenerationCannotResurrectSession() {
        var session = service.create(1L);
        when(recommendations.recommend(any(), any(), org.mockito.ArgumentMatchers.anyInt())).thenAnswer(inv -> {
            service.delete(1L, session.getId()); return new com.campus.competition.vo.StudentRecommendationVO();
        });
        assertThrows(BusinessException.class, () -> service.chat(1L, session.getId(), 0, "推荐"));
        assertEquals(404, assertThrows(BusinessException.class, () -> service.get(1L, session.getId())).getCode());
    }
    @Test void cancelInvalidatesPendingSnapshotEvenBeforeGenerationStarts() {
        var session = service.create(1L); service.cancel(1L, session.getId());
        assertEquals(409, assertThrows(BusinessException.class, () -> service.chat(1L, session.getId(), 0, "推荐")).getCode());
        assertEquals(1, service.get(1L, session.getId()).getVersion());
    }

    @Test
    @DisplayName("创建会话返回档案摘要与缺失项")
    void createsSessionWithProfileSummary() {
        AdviceSessionVO session = service.create(1L);
        assertTrue(session.getId() != null && !session.getId().isBlank());
        assertEquals("软件工程", session.getProfileSummary().getMajor());
        assertEquals(6, session.getProfileSummary().getWeeklyHours());
        assertTrue(session.getMissingFields().contains("技能标签"));
    }

    @Test
    @DisplayName("他人无法读取或修改会话")
    void rejectsOtherUsersSession() {
        String id = service.create(1L).getId();
        BusinessException forbidden = assertThrows(BusinessException.class, () -> service.get(2L, id));
        assertEquals(403, forbidden.getCode());
        assertThrows(BusinessException.class, () -> service.delete(2L, id));
    }

    @Test
    @DisplayName("版本号不一致时拒绝覆盖需求摘要")
    void rejectsStalePreferenceVersion() {
        AdviceSessionVO session = service.create(1L);
        AdvicePreferences preferences = session.getPreferences().copy();
        preferences.setWeeklyHours(3);
        AdviceSessionVO updated = service.updatePreferences(1L, session.getId(), session.getVersion(), preferences);
        assertEquals(3, updated.getPreferences().getWeeklyHours());
        BusinessException conflict = assertThrows(BusinessException.class,
                () -> service.updatePreferences(1L, session.getId(), session.getVersion(), preferences));
        assertEquals(409, conflict.getCode());
    }

    @Test
    @DisplayName("单条消息超过 2000 字被拒绝")
    void rejectsTooLongMessage() {
        String id = service.create(1L).getId();
        assertThrows(BusinessException.class, () -> service.message(1L, id, "a".repeat(2001)));
    }

    @Test
    @DisplayName("消息与摘要更新都能写回会话，清空后会话不存在")
    void messageAndDelete() {
        String id = service.create(1L).getId();
        AdviceMessageResultVO result = service.message(1L, id, "每周 3 小时，想做前端");
        assertTrue(result.getReply() != null && !result.getReply().isBlank());
        assertEquals(3, result.getPreferences().getWeeklyHours());
        assertEquals(1, result.getVersion());
        service.delete(1L, id);
        BusinessException missing = assertThrows(BusinessException.class, () -> service.get(1L, id));
        assertEquals(404, missing.getCode());
    }

    @Test
    @DisplayName("Redis 不可用时返回 503，不伪造会话")
    void redisFailureIsExplicit() {
        AdviceSessionService failing = new AdviceSessionService(new FailingStore(),
                mock(AdvicePreferenceService.class), mock(StudentRecommendationService.class),
                new AdviceProperties(), new ObjectMapper().findAndRegisterModules(), userMapper, mock(UserSkillMapper.class),
                mock(SkillTagMapper.class), mock(AchievementMapper.class));
        BusinessException error = assertThrows(BusinessException.class, () -> failing.create(1L));
        assertEquals(503, error.getCode());
        assertTrue(error.getMessage().contains("Redis"));
    }

    // ==================== 返修回归：报告 R5 ====================

    @Test
    @DisplayName("读取只续期 TTL，不回写会话正文")
    void getOnlyRenewsTtl() {
        AdviceSessionVO session = service.create(1L);
        int writesBefore = store.casCount.get();
        int touchesBefore = store.touchCount.get();

        service.get(1L, session.getId());

        assertEquals(writesBefore, store.casCount.get(), "读取不能重写正文");
        assertEquals(touchesBefore + 1, store.touchCount.get(), "读取应只续期");
    }

    @Test
    @DisplayName("两个请求都基于 version=0 编辑时只有一个成功，另一个返回 409")
    void onlyOneOfTwoSameVersionEditsWins() {
        AdviceSessionVO session = service.create(1L);
        // 模拟两个页面都读到 version=0：load 一直返回创建时的快照
        AdviceSessionService racing = new AdviceSessionService(
                new StaleLoadStore(store, session.getId()),
                mock(AdvicePreferenceService.class), mock(StudentRecommendationService.class),
                new AdviceProperties(), new ObjectMapper().findAndRegisterModules(), userMapper,
                mock(UserSkillMapper.class), mock(SkillTagMapper.class), mock(AchievementMapper.class));

        AdvicePreferences three = new AdvicePreferences();
        three.setWeeklyHours(3);
        racing.updatePreferences(1L, session.getId(), 0, three);

        AdvicePreferences five = new AdvicePreferences();
        five.setWeeklyHours(5);
        BusinessException conflict = assertThrows(BusinessException.class,
                () -> racing.updatePreferences(1L, session.getId(), 0, five));
        assertEquals(409, conflict.getCode());

        assertEquals(3, service.get(1L, session.getId()).getPreferences().getWeeklyHours(),
                "后到的旧版本写入不能覆盖新需求");
    }

    @Test
    @DisplayName("会话被清空后，迟到的消息结果不能复活会话")
    void lateMessageCannotReviveDeletedSession() throws Exception {
        AdvicePreferenceService slow = mock(AdvicePreferenceService.class);
        java.util.concurrent.CountDownLatch inModel = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        when(slow.apply(anyString(), any(AdvicePreferences.class), any())).thenAnswer(invocation -> {
            inModel.countDown();
            release.await(5, java.util.concurrent.TimeUnit.SECONDS);
            AdvicePreferenceService.Applied applied = new AdvicePreferenceService.Applied();
            applied.preferences = invocation.getArgument(1);
            applied.reply = "好的";
            return applied;
        });
        AdviceSessionService target = new AdviceSessionService(store, slow,
                mock(StudentRecommendationService.class), new AdviceProperties(),
                new ObjectMapper().findAndRegisterModules(), userMapper, mock(UserSkillMapper.class),
                mock(SkillTagMapper.class), mock(AchievementMapper.class));
        String id = target.create(1L).getId();

        java.util.concurrent.ExecutorService executor =
                java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            java.util.concurrent.Future<?> pending =
                    executor.submit(() -> target.message(1L, id, "每周 3 小时"));
            assertTrue(inModel.await(5, java.util.concurrent.TimeUnit.SECONDS));
            target.delete(1L, id);
            release.countDown();

            java.util.concurrent.ExecutionException failure = assertThrows(
                    java.util.concurrent.ExecutionException.class,
                    () -> pending.get(5, java.util.concurrent.TimeUnit.SECONDS));
            BusinessException cause = (BusinessException) failure.getCause();
            assertEquals(404, cause.getCode());
            assertEquals(404, assertThrows(BusinessException.class,
                    () -> target.get(1L, id)).getCode(), "会话不能被旧请求复活");
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    /** 与 Redis Lua 等价的原子版本比较：在同一个锁内读版本再写。 */
    private static final class InMemoryStore implements AdviceSessionStore {
        private final Map<String, String> values = new LinkedHashMap<>();
        private final Map<String, Integer> versions = new LinkedHashMap<>();
        final java.util.concurrent.atomic.AtomicInteger touchCount =
                new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.atomic.AtomicInteger casCount =
                new java.util.concurrent.atomic.AtomicInteger();

        @Override
        public synchronized Optional<String> load(String sessionId) {
            return Optional.ofNullable(values.get(sessionId));
        }

        @Override
        public synchronized void save(String sessionId, String json, Duration ttl) {
            values.put(sessionId, json);
            versions.put(sessionId, 0);
        }

        @Override
        public synchronized boolean touch(String sessionId, Duration ttl) {
            touchCount.incrementAndGet();
            return values.containsKey(sessionId);
        }

        @Override
        public synchronized SaveOutcome saveIfVersion(String sessionId, int expectedVersion,
                                                     String json, Duration ttl) {
            casCount.incrementAndGet();
            Integer current = versions.get(sessionId);
            if (current == null) return SaveOutcome.MISSING;
            if (current != expectedVersion) return SaveOutcome.VERSION_CONFLICT;
            values.put(sessionId, json);
            versions.put(sessionId, expectedVersion + 1);
            return SaveOutcome.SAVED;
        }

        @Override
        public synchronized void delete(String sessionId) {
            values.remove(sessionId);
            versions.remove(sessionId);
        }
    }

    /**
     * 模拟“两个请求都读到同一个旧版本”：load 固定返回创建时的快照，
     * 但写入仍按真实版本做原子比较。
     */
    private static final class StaleLoadStore implements AdviceSessionStore {
        private final InMemoryStore delegate;
        private final String fixed;

        StaleLoadStore(InMemoryStore delegate, String sessionId) {
            this.delegate = delegate;
            this.fixed = delegate.load(sessionId).orElseThrow();
        }

        @Override
        public Optional<String> load(String sessionId) {
            return Optional.of(fixed);
        }

        @Override
        public void save(String sessionId, String json, Duration ttl) {
            delegate.save(sessionId, json, ttl);
        }

        @Override
        public boolean touch(String sessionId, Duration ttl) {
            return delegate.touch(sessionId, ttl);
        }

        @Override
        public SaveOutcome saveIfVersion(String sessionId, int expectedVersion, String json, Duration ttl) {
            return delegate.saveIfVersion(sessionId, expectedVersion, json, ttl);
        }

        @Override
        public void delete(String sessionId) {
            delegate.delete(sessionId);
        }
    }

    private static final class FailingStore implements AdviceSessionStore {
        @Override
        public Optional<String> load(String sessionId) {
            throw new BusinessException(503, "推荐会话存储暂不可用（Redis 未连接），请稍后重试");
        }

        @Override
        public void save(String sessionId, String json, Duration ttl) {
            throw new BusinessException(503, "推荐会话存储暂不可用（Redis 未连接），请稍后重试");
        }

        @Override
        public boolean touch(String sessionId, Duration ttl) {
            throw new BusinessException(503, "推荐会话存储暂不可用（Redis 未连接），请稍后重试");
        }

        @Override
        public SaveOutcome saveIfVersion(String sessionId, int expectedVersion, String json, Duration ttl) {
            throw new BusinessException(503, "推荐会话存储暂不可用（Redis 未连接），请稍后重试");
        }

        @Override
        public void delete(String sessionId) {
            throw new BusinessException(503, "推荐会话存储暂不可用（Redis 未连接），请稍后重试");
        }
    }
}
