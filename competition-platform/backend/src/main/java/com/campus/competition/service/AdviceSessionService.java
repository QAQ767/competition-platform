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

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AdviceSessionService.class);
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

    private final Map<String, LlmCallBudget> active = new ConcurrentHashMap<>();

    public void cancel(Long userId, String sessionId) {
        AdviceSession session = requireSession(userId, sessionId);
        int base = session.getVersion();
        session.setVersion(base + 1);
        saveIfVersion(session, base);
        LlmCallBudget budget = active.get(sessionId);
        if (budget != null) budget.cancel();
    }

    public AdviceMessageResultVO chat(Long userId, String sessionId, Integer version, String message) {
        AdviceSession session = requireSession(userId, sessionId);
        if (version == null || !version.equals(session.getVersion())) throw new BusinessException(409, "会话已更新，请刷新后重试");
        if (message == null || message.isBlank() || message.length() > properties.getMaxMessageLength()) throw new BusinessException(400, "请输入 1 到 2000 字的需求");
        checkRate(userId, System.currentTimeMillis());
        try (LlmCallBudget budget = new LlmCallBudget(properties.getTurnTimeoutSeconds())) {
            if (active.putIfAbsent(sessionId, budget) != null) throw new BusinessException(409, "该会话正在生成，请先停止或等待");
            try {
                String text = message.trim();
                StudentRecommendationVO previous = session.getMessages().stream().filter(m -> m.getRecommendation() != null)
                        .reduce((a,b) -> b).map(AdviceMessage::getRecommendation).orElse(null);
                String reply; String mode = "RULE"; String fallback = null; StudentRecommendationVO recommendation = null;
                List<String> questions = new ArrayList<>();
                boolean greeting = text.matches("(?i)(?:你好|您好|嗨|谢谢|谢谢你|好的|好|hi|hello|你是谁|在吗)[！!。.?？ ]*");
                boolean explain = text.matches("(?s).*(?:(?:为什么|为何|解释).*(?:推荐|这[个条支场]|队伍|比赛)|推荐理由).*");
                boolean relevant = text.matches("(?s).*(?:推荐|比赛|竞赛|赛事|参加|队伍|团队|换一些|换一批|换几个|其他推荐).*" )
                        || !new com.campus.competition.advice.RulePreferenceExtractor().extract(text).changes.isEmpty();
                if (greeting) reply = "你好！我可以帮你找比赛、找队伍。告诉我技能、每周时间或想参加的赛事即可。";
                else if (explain) reply = previous == null ? "目前还没有推荐结果。可以先说‘按我的情况推荐比赛和队伍’。" : explanation(previous, text);
                else if (!relevant) reply = "我可以结合档案帮你找比赛、找队伍。你希望先看哪一类？也可以直接告诉我想参加的赛事。";
                else {
                    ParticipantProfile profile = profile(userId);
                    long stageStart = System.nanoTime();
                    AdvicePreferenceService.Applied applied = preferenceService.applyChat(text, session.getPreferences(), profile, recommendationService.competitionCatalog());
                    log.info("advice phase=understand elapsedMs={} mode={}", (System.nanoTime()-stageStart)/1_000_000, applied.mode);
                    budget.check(); session.setPreferences(applied.preferences);
                    if (text.matches("(?s).*(?:换一些|换一批|换几个|其他推荐).*" ) && previous != null) {
                        for (var c : previous.getCompetitions()) if (!session.getPreferences().getExcludedCompetitionIds().contains(c.getId())) session.getPreferences().getExcludedCompetitionIds().add(c.getId());
                        for (var t : previous.getTeams()) if (!session.getPreferences().getExcludedTeamIds().contains(t.getId())) session.getPreferences().getExcludedTeamIds().add(t.getId());
                    }
                    questions = applied.questions;
                    if (applied.clarificationRequired) {
                        reply = String.join("\n", applied.questions);
                        mode = applied.mode;
                        fallback = applied.fallbackReason;
                    } else {
                        stageStart = System.nanoTime();
                        recommendation = recommendationService.recommend(profile, session.getPreferences(), resolveWindow(null, session.getPreferences().getWindowDays()));
                        log.info("advice phase=recommend elapsedMs={} retry={} failure={}", (System.nanoTime()-stageStart)/1_000_000, recommendation.getRetryCount(), recommendation.getFailureCategory());
                        budget.check(); mode = recommendation.getMode(); fallback = recommendation.getFallbackReason();
                        int count = recommendation.getCompetitions().size() + recommendation.getTeams().size();
                        reply = count == 0 ? "当前没有符合这些条件的更多结果。我保留了你的明确条件；可以尝试放宽时间范围，或稍后再查看招募。" :
                                "已结合档案与本轮需求筛选，找到 " + recommendation.getCompetitions().size() + " 场竞赛、" + recommendation.getTeams().size() + " 支队伍。请核对卡片中的时间与资格。";
                        if (session.getPreferences().getWeeklyHours() != null) reply += " 每周按 " + session.getPreferences().getWeeklyHours() + " 小时考虑。";
                        if (!session.getPreferences().getExcludedKeywords().isEmpty()) reply += " 已排除：" + String.join("、", session.getPreferences().getExcludedKeywords()) + "。";
                        if (session.getPreferences().isWeeklyHoursUnrestricted()) reply += " 已取消本轮每周时间限制。";
                        if (session.getPreferences().isWindowUnrestricted()) reply += " 已取消本轮日期范围限制，仍核查报名截止。";
                    }
                }
                budget.check();
                AdviceMessage answer = new AdviceMessage("assistant", reply, FMT.format(LocalDateTime.now())); answer.setRecommendation(recommendation); answer.setQuestions(questions);
                session.getMessages().add(new AdviceMessage("user", text, FMT.format(LocalDateTime.now()))); session.getMessages().add(answer); trim(session);
                session.setVersion(version + 1); session.setUpdatedAt(LocalDateTime.now()); saveIfVersion(session, version);
                AdviceMessageResultVO result = new AdviceMessageResultVO(); result.setSessionId(sessionId); result.setVersion(session.getVersion()); result.setReply(reply);
                result.setPreferences(session.getPreferences()); result.setRecommendation(recommendation); result.setMode(mode); result.setFallbackReason(fallback); result.setAt(answer.getAt()); result.setQuestions(questions); return result;
            } finally { active.remove(sessionId, budget); }
        }
    }

    private String explanation(StudentRecommendationVO previous, String text) {
        List<String> reasons = new ArrayList<>();
        for (var t : previous.getTeams()) if (text.contains(t.getTitle())) reasons.add(t.getTitle() + "：" + t.getReason());
        for (var c : previous.getCompetitions()) if (text.contains(c.getName())) reasons.add(c.getName() + "：" + c.getReason());
        if (reasons.isEmpty() && !previous.getTeams().isEmpty()) { var t = previous.getTeams().get(0); reasons.add(t.getTitle() + "：" + t.getReason()); }
        if (reasons.isEmpty() && !previous.getCompetitions().isEmpty()) { var c = previous.getCompetitions().get(0); reasons.add(c.getName() + "：" + c.getReason()); }
        return reasons.isEmpty() ? "上一轮没有符合条件的结果，可以调整时间或赛事范围。" : String.join("\n", reasons) + "\n这是上次结果的解释，当前是否可申请请以详情页实时状态为准。";
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
        cancel(userId, sessionId);
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
        if (!java.util.Set.of("COMPETITIONS", "TEAMS", "BOTH").contains(prefs.getOutputTarget())) throw new BusinessException(400, "推荐目标不正确");
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
