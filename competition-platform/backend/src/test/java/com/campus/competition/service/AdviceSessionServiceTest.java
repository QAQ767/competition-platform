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
        service = new AdviceSessionService(store, preferenceService, mock(StudentRecommendationService.class),
                properties, new ObjectMapper().findAndRegisterModules(), userMapper, mock(UserSkillMapper.class),
                mock(SkillTagMapper.class), mock(AchievementMapper.class));
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
