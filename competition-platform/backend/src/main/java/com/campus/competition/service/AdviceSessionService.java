package com.campus.competition.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.competition.advice.AdviceMessage;
import com.campus.competition.advice.AdvicePreferences;
import com.campus.competition.advice.AdviceSession;
import com.campus.competition.advice.ParticipantProfile;
import com.campus.competition.common.BusinessException;
import com.campus.competition.config.AdviceProperties;
import com.campus.competition.entity.Achievement;
import com.campus.competition.entity.User;
import com.campus.competition.mapper.AchievementMapper;
import com.campus.competition.mapper.SkillTagMapper;
import com.campus.competition.mapper.UserMapper;
import com.campus.competition.mapper.UserSkillMapper;
import com.campus.competition.support.UserVOHelper;
import com.campus.competition.vo.AdviceMessageResultVO;
import com.campus.competition.vo.AdviceProfileSummaryVO;
import com.campus.competition.vo.AdviceSessionVO;
import com.campus.competition.vo.StudentRecommendationVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 学生推荐会话：Redis 按会话 ID 保存，校验 userId 归属，24 小时 TTL，最多保留最近 10 轮。
 * 模型调用期间不持有数据库事务。
 */
@Service
public class AdviceSessionService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long RATE_WINDOW_MILLIS = 60_000L;

    private final AdviceSessionStore store;
    private final AdvicePreferenceService preferenceService;
    private final StudentRecommendationService recommendationService;
    private final AdviceProperties properties;
    private final ObjectMapper objectMapper;
    private final UserMapper userMapper;
    private final UserSkillMapper userSkillMapper;
    private final SkillTagMapper skillTagMapper;
    private final AchievementMapper achievementMapper;
    private final Map<Long, Deque<Long>> requestTimes = new ConcurrentHashMap<>();

    public AdviceSessionService(AdviceSessionStore store,
                                AdvicePreferenceService preferenceService,
                                StudentRecommendationService recommendationService,
                                AdviceProperties properties,
                                ObjectMapper objectMapper,
                                UserMapper userMapper,
                                UserSkillMapper userSkillMapper,
                                SkillTagMapper skillTagMapper,
                                AchievementMapper achievementMapper) {
        this.store = store;
        this.preferenceService = preferenceService;
        this.recommendationService = recommendationService;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.userMapper = userMapper;
        this.userSkillMapper = userSkillMapper;
        this.skillTagMapper = skillTagMapper;
        this.achievementMapper = achievementMapper;
    }

    public AdviceSessionVO create(Long userId) {
        ParticipantProfile profile = profile(userId);
        AdviceSession session = new AdviceSession();
        session.setId(UUID.randomUUID().toString().replace("-", ""));
        session.setUserId(userId);
        session.setVersion(0);
        session.setPreferences(new AdvicePreferences());
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
        save(session);
        return toVO(session, profile);
    }

    public AdviceSessionVO get(Long userId, String sessionId) {
        AdviceSession session = requireSession(userId, sessionId);
        // 读取只续期，不回写正文：旧快照回写会覆盖并发的新编辑。
        store.touch(sessionId, ttl());
        return toVO(session, profile(userId));
    }

    public AdviceMessageResultVO message(Long userId, String sessionId, String message) {
        AdviceSession session = requireSession(userId, sessionId);
        int baseVersion = session.getVersion();
        checkRate(userId, System.currentTimeMillis());
        if (message == null || message.isBlank()) {
            throw new BusinessException(400, "请输入要补充的需求");
        }
        if (message.length() > properties.getMaxMessageLength()) {
            throw new BusinessException(400, "单条消息最长 " + properties.getMaxMessageLength() + " 字");
        }
        ParticipantProfile profile = profile(userId);
        AdvicePreferenceService.Applied applied =
                preferenceService.apply(message, session.getPreferences(), profile);

        session.setPreferences(applied.preferences);
        session.setVersion(baseVersion + 1);
        session.setUpdatedAt(LocalDateTime.now());
        AdviceMessage userMessage = new AdviceMessage("user", message.trim(), FMT.format(LocalDateTime.now()));
        AdviceMessage assistantMessage = new AdviceMessage("assistant", applied.reply, FMT.format(LocalDateTime.now()));
        for (var change : applied.changes) assistantMessage.getChanges().add(change.display());
        assistantMessage.getQuestions().addAll(applied.questions);
        session.getMessages().add(userMessage);
        session.getMessages().add(assistantMessage);
        trim(session);
        // 模型处理期间若用户改了需求或清空了会话，这里的旧结果不能覆盖或复活它。
        saveIfVersion(session, baseVersion);

        AdviceMessageResultVO vo = new AdviceMessageResultVO();
        vo.setSessionId(session.getId());
        vo.setVersion(session.getVersion());
        vo.setReply(applied.reply);
        vo.setMode(applied.mode);
        vo.setFallbackReason(applied.fallbackReason);
        vo.setChanges(applied.changes);
        vo.setQuestions(applied.questions);
        vo.setPreferences(session.getPreferences());
        vo.setAt(FMT.format(LocalDateTime.now()));
        return vo;
    }

    public AdviceSessionVO updatePreferences(Long userId, String sessionId, Integer version,
                                             AdvicePreferences preferences) {
        AdviceSession session = requireSession(userId, sessionId);
        if (version == null || !version.equals(session.getVersion())) {
            throw new BusinessException(409, "需求摘要已在其他页面更新，请刷新后重试");
        }
        if (preferences == null) {
            throw new BusinessException(400, "需求摘要不能为空");
        }
        validate(preferences);
        AdvicePreferences next = preferences.copy();
        // 手动确认的字段标记为 MANUAL；未变化的字段保留原来源。
        AdvicePreferences old = session.getPreferences();
        markManual(next, old, "weeklyHours", next.getWeeklyHours(), old.getWeeklyHours());
        markManual(next, old, "directions", next.getDirections(), old.getDirections());
        markManual(next, old, "skills", next.getSkills(), old.getSkills());
        markManual(next, old, "roles", next.getRoles(), old.getRoles());
        markManual(next, old, "difficulty", next.getDifficulty(), old.getDifficulty());
        markManual(next, old, "excludedKeywords", next.getExcludedKeywords(), old.getExcludedKeywords());
        markManual(next, old, "windowDays", next.getWindowDays(), old.getWindowDays());
        markManual(next, old, "teamOnly", next.getTeamOnly(), old.getTeamOnly());
        markManual(next, old, "note", next.getNote(), old.getNote());

        session.setPreferences(next);
        int baseVersion = session.getVersion();
        session.setVersion(baseVersion + 1);
        session.setUpdatedAt(LocalDateTime.now());
        saveIfVersion(session, baseVersion);
        return toVO(session, profile(userId));
    }

    public StudentRecommendationVO recommend(Long userId, String sessionId, Integer windowDays) {
        AdviceSession session = requireSession(userId, sessionId);
        checkRate(userId, System.currentTimeMillis());
        ParticipantProfile profile = profile(userId);
        int days = resolveWindow(windowDays, session.getPreferences().getWindowDays());
        return recommendationService.recommend(profile, session.getPreferences(), days);
    }

    /** 不依赖 Redis 的档案推荐，便于 P1 在会话存储不可用时仍可独立验收。 */
    public StudentRecommendationVO recommendFromProfile(Long userId, Integer windowDays) {
        checkRate(userId, System.currentTimeMillis());
        ParticipantProfile profile = profile(userId);
        int days = resolveWindow(windowDays, null);
        return recommendationService.recommend(profile, new AdvicePreferences(), days);
    }

    public void delete(Long userId, String sessionId) {
        requireSession(userId, sessionId);
        store.delete(sessionId);
    }

    public ParticipantProfile profile(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        List<String> skills = UserVOHelper.toSkillNames(userId, userSkillMapper, skillTagMapper);
        List<String> achievements = achievementMapper.selectList(
                        new LambdaQueryWrapper<Achievement>().eq(Achievement::getUserId, userId))
                .stream().map(Achievement::getName).filter(java.util.Objects::nonNull).toList();
        return ParticipantProfile.of(user, skills, achievements);
    }

    private AdviceSession requireSession(Long userId, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new BusinessException(400, "缺少会话 ID");
        }
        String json = store.load(sessionId)
                .orElseThrow(() -> new BusinessException(404, "会话不存在或已过期，请重新开始"));
        try {
            AdviceSession session = objectMapper.readValue(json, AdviceSession.class);
            if (session.getUserId() == null || !session.getUserId().equals(userId)) {
                throw new BusinessException(403, "无权访问该会话");
            }
            return session;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "会话数据损坏，请清空后重试");
        }
    }

    private void save(AdviceSession session) {
        try {
            store.save(session.getId(), objectMapper.writeValueAsString(session), ttl());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "会话保存失败");
        }
    }

    /** 原子版本比较写入；版本不一致返回 409，会话已被清空返回 404。 */
    private void saveIfVersion(AdviceSession session, int expectedVersion) {
        AdviceSessionStore.SaveOutcome outcome;
        try {
            outcome = store.saveIfVersion(session.getId(), expectedVersion,
                    objectMapper.writeValueAsString(session), ttl());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "会话保存失败");
        }
        if (outcome == AdviceSessionStore.SaveOutcome.MISSING) {
            throw new BusinessException(404, "会话不存在或已过期，请重新开始");
        }
        if (outcome == AdviceSessionStore.SaveOutcome.VERSION_CONFLICT) {
            throw new BusinessException(409, "需求已在其他页面更新，请刷新后重试");
        }
    }

    private Duration ttl() {
        return Duration.ofHours(properties.getSessionTtlHours());
    }

    private void trim(AdviceSession session) {
        int max = properties.getMaxMessages();
        if (session.getMessages().size() > max) {
            session.setMessages(new ArrayList<>(
                    session.getMessages().subList(session.getMessages().size() - max, session.getMessages().size())));
        }
    }

    private int resolveWindow(Integer requested, Integer confirmed) {
        Integer value = requested != null ? requested : confirmed;
        if (value == null) value = properties.getDefaultWindowDays();
        if (value < properties.getMinWindowDays() || value > properties.getMaxWindowDays()) {
            throw new BusinessException(400, "时间范围需在 " + properties.getMinWindowDays()
                    + " 到 " + properties.getMaxWindowDays() + " 天之间");
        }
        return value;
    }

    private void validate(AdvicePreferences prefs) {
        if (prefs.getWeeklyHours() != null && (prefs.getWeeklyHours() < 1 || prefs.getWeeklyHours() > 80)) {
            throw new BusinessException(400, "每周时间需在 1 到 80 小时之间");
        }
        if (prefs.getWindowDays() != null
                && (prefs.getWindowDays() < properties.getMinWindowDays()
                || prefs.getWindowDays() > properties.getMaxWindowDays())) {
            throw new BusinessException(400, "时间范围需在 " + properties.getMinWindowDays()
                    + " 到 " + properties.getMaxWindowDays() + " 天之间");
        }
        limitList(prefs.getDirections(), "方向");
        limitList(prefs.getSkills(), "技能");
        limitList(prefs.getRoles(), "角色");
        limitList(prefs.getExcludedKeywords(), "排除项");
        if (prefs.getNote() != null && prefs.getNote().length() > 500) {
            throw new BusinessException(400, "备注最长 500 字");
        }
        if (prefs.getDifficulty() != null && prefs.getDifficulty().length() > 10) {
            throw new BusinessException(400, "难度偏好过长");
        }
    }

    private void limitList(List<String> list, String label) {
        if (list.size() > 12) throw new BusinessException(400, label + "最多 12 项");
        for (String value : list) {
            if (value == null || value.isBlank() || value.length() > 30) {
                throw new BusinessException(400, label + "存在空值或过长内容");
            }
        }
    }

    private void markManual(AdvicePreferences next, AdvicePreferences old, String field,
                            Object newValue, Object oldValue) {
        if (!java.util.Objects.equals(newValue, oldValue)) {
            next.markSource(field, AdvicePreferences.SOURCE_MANUAL);
        }
    }

    private void checkRate(Long userId, long now) {
        Deque<Long> times = requestTimes.computeIfAbsent(userId, ignored -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && now - times.peekFirst() >= RATE_WINDOW_MILLIS) times.removeFirst();
            if (times.size() >= properties.getMaxRequestsPerMinute()) {
                throw new BusinessException(429, "操作太频繁，请稍后再试");
            }
            times.addLast(now);
        }
    }

    private AdviceSessionVO toVO(AdviceSession session, ParticipantProfile profile) {
        AdviceSessionVO vo = new AdviceSessionVO();
        vo.setId(session.getId());
        vo.setVersion(session.getVersion());
        vo.setPreferences(session.getPreferences());
        vo.setMessages(session.getMessages());
        vo.setMissingFields(profile.missingFields());
        vo.setCreatedAt(session.getCreatedAt() == null ? null : FMT.format(session.getCreatedAt()));
        vo.setUpdatedAt(session.getUpdatedAt() == null ? null : FMT.format(session.getUpdatedAt()));

        AdviceProfileSummaryVO summary = new AdviceProfileSummaryVO();
        summary.setNickname(profile.getNickname());
        summary.setCollege(profile.getCollege());
        summary.setMajor(profile.getMajor());
        summary.setSkills(profile.getSkills());
        summary.setPreferredRoles(profile.getPreferredRoles());
        summary.setWeeklyHours(profile.getWeeklyHours());
        summary.setWeeklyHoursKnown(profile.effectiveWeeklyHours() != null);
        summary.setCompeteStatus(profile.getCompeteStatus());
        summary.setHasProjectExperience(profile.hasProjectEvidence());
        vo.setProfileSummary(summary);
        return vo;
    }
}
