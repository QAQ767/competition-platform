package com.campus.competition.service;

import com.campus.competition.common.UserContext;
import com.campus.competition.common.BusinessException;
import com.campus.competition.entity.*;
import com.campus.competition.mapper.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.mockito.ArgumentCaptor;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class AiServiceTest {
    UserMapper users = mock(UserMapper.class);
    UserSkillMapper skills = mock(UserSkillMapper.class);
    SkillTagMapper tags = mock(SkillTagMapper.class);
    TeamMapper teams = mock(TeamMapper.class);
    TeamMemberMapper members = mock(TeamMemberMapper.class);
    TeamApplicationMapper applications = mock(TeamApplicationMapper.class);
    CompetitionMapper competitions = mock(CompetitionMapper.class);
    AchievementMapper achievements = mock(AchievementMapper.class);
    LlmService llm = mock(LlmService.class);
    AiService service = new AiService(users, skills, tags, teams, members, applications, competitions, achievements, llm);

    Team setup() {
        Team team = new Team(); team.setId(1L); team.setCaptainId(10L); team.setCompetitionId(1L);
        team.setMemberCount(1); team.setMaxMembers(3); team.setDescription("校园商城，缺前端"); team.setStatus("招募中");
        when(teams.selectById(1L)).thenReturn(team);
        when(members.selectList(any())).thenReturn(Collections.emptyList());
        when(applications.selectList(any())).thenReturn(Collections.emptyList());
        when(skills.selectList(any())).thenReturn(Collections.emptyList());
        User allowed = new User(); allowed.setId(2L); allowed.setRole("STUDENT"); allowed.setCompeteStatus("WANT_INVITE");
        allowed.setIntro("擅长前端"); allowed.setProjectExperience("负责Vue商城页面与联调");
        allowed.setPreferredRoles("前端开发"); allowed.setWeeklyHours(12); allowed.setEmail("private@example.com");
        User optedOut = new User(); optedOut.setId(3L); optedOut.setRole("STUDENT"); optedOut.setCompeteStatus("NOT_PARTICIPATE");
        when(users.selectList(null)).thenReturn(Arrays.asList(allowed, optedOut));
        when(users.selectById(2L)).thenReturn(allowed);
        UserContext.setUserId(10L);
        return team;
    }
    @AfterEach void cleanup() { UserContext.clear(); }

    @Test void profileBecomesModelInputWithoutContactDataAndOptedOutUser() {
        setup(); when(llm.generateAssessment(anyString())).thenReturn(null);
        var result = service.recommend(1L);
        assertEquals(1, result.size()); assertEquals("RULE", result.get(0).getReasonSource());
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class); verify(llm).generateAssessment(prompt.capture());
        assertTrue(prompt.getValue().contains("负责Vue商城页面与联调"));
        assertTrue(prompt.getValue().contains("weeklyHours"));
        assertFalse(prompt.getValue().contains("private@example.com"));
        assertFalse(prompt.getValue().contains("NOT_PARTICIPATE"));
    }
    @Test void onlyCaptainAndOpenRecruitmentCanTriggerAnalysis() {
        Team team = setup(); UserContext.setUserId(99L);
        assertThrows(BusinessException.class, () -> service.recommend(1L));
        UserContext.setUserId(10L); team.setStatus("已结束");
        assertThrows(BusinessException.class, () -> service.recommend(1L));
        verifyNoInteractions(llm);
    }
}
