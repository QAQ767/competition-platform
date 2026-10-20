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

    @BeforeEach
    void setUp() {
        CompetitionMapper competitionMapper = mock(CompetitionMapper.class);
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
        LlmProperties llmProperties = new LlmProperties(); // 未配置 → 规则模式，不触网

        service = new StudentRecommendationService(competitionMapper, mock(TeamMapper.class),
                mock(TeamMemberMapper.class), mock(TeamApplicationMapper.class),
                mock(UserMapper.class), mock(UserSkillMapper.class), mock(SkillTagMapper.class),
                eligibility, mock(LlmService.class), llmProperties, new ObjectMapper());

        User user = new User();
        user.setId(7L);
        user.setNickname("前端同学");
        user.setMajor("软件工程");
        user.setWeeklyHours(8);
        user.setCompeteStatus("WANT_INVITE");
        user.setIntro("喜欢 Vue");
        profile = ParticipantProfile.of(user, List.of("前端"), List.of());
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
