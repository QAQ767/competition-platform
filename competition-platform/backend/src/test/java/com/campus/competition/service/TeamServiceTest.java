package com.campus.competition.service;

import com.campus.competition.common.BusinessException;
import com.campus.competition.entity.Team;
import com.campus.competition.entity.TeamApplication;
import com.campus.competition.entity.TeamMember;
import com.campus.competition.entity.User;
import com.campus.competition.mapper.CompetitionMapper;
import com.campus.competition.mapper.TeamApplicationMapper;
import com.campus.competition.mapper.TeamExitLogMapper;
import com.campus.competition.mapper.TeamMapper;
import com.campus.competition.mapper.TeamMemberMapper;
import com.campus.competition.mapper.UserMapper;
import com.campus.competition.vo.TeamVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 队伍核心流程单元测试（Mockito，不依赖数据库）
 */
class TeamServiceTest {

    private TeamMapper teamMapper;
    private TeamMemberMapper teamMemberMapper;
    private TeamApplicationMapper teamApplicationMapper;
    private TeamExitLogMapper teamExitLogMapper;
    private CompetitionMapper competitionMapper;
    private UserMapper userMapper;
    private NotificationService notificationService;
    private TeamService teamService;

    private Team team;
    private User captain;
    private User target;

    @BeforeEach
    void setUp() {
        teamMapper = mock(TeamMapper.class);
        teamMemberMapper = mock(TeamMemberMapper.class);
        teamApplicationMapper = mock(TeamApplicationMapper.class);
        teamExitLogMapper = mock(TeamExitLogMapper.class);
        competitionMapper = mock(CompetitionMapper.class);
        userMapper = mock(UserMapper.class);
        notificationService = mock(NotificationService.class);
        teamService = new TeamService(teamMapper, teamMemberMapper, teamApplicationMapper,
                teamExitLogMapper, competitionMapper, userMapper, notificationService);

        team = new Team();
        team.setId(1L);
        team.setTitle("数学建模组队");
        team.setCaptainId(1L);
        team.setMemberCount(1);
        team.setMaxMembers(3);
        team.setStatus("招募中");
        team.setCompetitionId(1L);

        captain = new User();
        captain.setId(1L);
        captain.setNickname("队长");

        target = new User();
        target.setId(2L);
        target.setNickname("队员");
    }

    // ===== 邀请 =====

    @Test
    @DisplayName("邀请：正常流程生成 invited=true 的申请并通知对方")
    void invite_happyPath() {
        when(teamMapper.selectById(1L)).thenReturn(team);
        when(userMapper.selectById(1L)).thenReturn(captain);
        when(userMapper.selectById(2L)).thenReturn(target);
        when(teamMemberMapper.selectCount(any())).thenReturn(0L);
        when(teamApplicationMapper.selectCount(any())).thenReturn(0L);

        teamService.invite(1L, 2L, 1L);

        verify(teamApplicationMapper).insert(any(TeamApplication.class));
        verify(notificationService).send(eq(2L), eq("INVITE"), any(String.class));
    }

    @Test
    @DisplayName("邀请：被邀请用户不存在时拒绝")
    void invite_targetNotFound() {
        when(teamMapper.selectById(1L)).thenReturn(team);
        when(userMapper.selectById(1L)).thenReturn(captain);
        when(userMapper.selectById(2L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> teamService.invite(1L, 2L, 1L));
        verify(teamApplicationMapper, never()).insert(any());
    }

    @Test
    @DisplayName("邀请：对方已是成员时拒绝")
    void invite_alreadyMember() {
        when(teamMapper.selectById(1L)).thenReturn(team);
        when(userMapper.selectById(1L)).thenReturn(captain);
        when(userMapper.selectById(2L)).thenReturn(target);
        when(teamMemberMapper.selectCount(any())).thenReturn(1L);

        assertThrows(BusinessException.class, () -> teamService.invite(1L, 2L, 1L));
        verify(teamApplicationMapper, never()).insert(any());
    }

    @Test
    @DisplayName("邀请：重复待审批邀请时拒绝")
    void invite_duplicatePending() {
        when(teamMapper.selectById(1L)).thenReturn(team);
        when(userMapper.selectById(1L)).thenReturn(captain);
        when(userMapper.selectById(2L)).thenReturn(target);
        when(teamMemberMapper.selectCount(any())).thenReturn(0L);
        when(teamApplicationMapper.selectCount(any())).thenReturn(1L);

        assertThrows(BusinessException.class, () -> teamService.invite(1L, 2L, 1L));
        verify(teamApplicationMapper, never()).insert(any());
    }

    @Test
    @DisplayName("邀请：队伍已满员时拒绝")
    void invite_teamFull() {
        team.setMemberCount(3);
        when(teamMapper.selectById(1L)).thenReturn(team);
        when(userMapper.selectById(1L)).thenReturn(captain);
        when(userMapper.selectById(2L)).thenReturn(target);

        assertThrows(BusinessException.class, () -> teamService.invite(1L, 2L, 1L));
        verify(teamApplicationMapper, never()).insert(any());
    }

    // ===== 接受邀请 =====

    @Test
    @DisplayName("接受邀请：入队并通知队长")
    void acceptInvite_happyPath() {
        TeamApplication app = pendingInvite(1L, 2L);
        when(teamApplicationMapper.selectById(1L)).thenReturn(app);
        when(teamMapper.selectById(1L)).thenReturn(team);
        when(teamMemberMapper.selectCount(any())).thenReturn(0L);

        teamService.acceptInvite(1L, 2L);

        verify(teamMemberMapper).insert(any(TeamMember.class));
        verify(teamMapper).updateById(team);
        assertEquals(2, team.getMemberCount());
        verify(notificationService).send(eq(1L), eq("INVITE_ACCEPTED"), any(String.class));
    }

    @Test
    @DisplayName("接受邀请：非邀请记录（自己申请）不可自接受")
    void acceptInvite_notInvited() {
        TeamApplication app = pendingInvite(1L, 2L);
        app.setInvited(false);
        when(teamApplicationMapper.selectById(1L)).thenReturn(app);

        assertThrows(BusinessException.class, () -> teamService.acceptInvite(1L, 2L));
        verify(teamMemberMapper, never()).insert(any());
    }

    @Test
    @DisplayName("接受邀请：已处理的邀请不可重复接受")
    void acceptInvite_alreadyProcessed() {
        TeamApplication app = pendingInvite(1L, 2L);
        app.setStatus("已通过");
        when(teamApplicationMapper.selectById(1L)).thenReturn(app);

        assertThrows(BusinessException.class, () -> teamService.acceptInvite(1L, 2L));
        verify(teamMemberMapper, never()).insert(any());
    }

    // ===== 拒绝邀请 =====

    @Test
    @DisplayName("拒绝邀请：更新状态并通知队长")
    void rejectInvite_happyPath() {
        TeamApplication app = pendingInvite(1L, 2L);
        when(teamApplicationMapper.selectById(1L)).thenReturn(app);
        when(teamMapper.selectById(1L)).thenReturn(team);

        teamService.rejectInvite(1L, 2L);

        assertEquals("已拒绝", app.getStatus());
        verify(teamApplicationMapper).updateById(app);
        verify(notificationService).send(eq(1L), eq("INVITE_DECLINED"), any(String.class));
    }

    // ===== 退出队伍 =====

    @Test
    @DisplayName("退出队伍：成员退出后人数减一，满员状态恢复招募")
    void leave_happyPath() {
        team.setMemberCount(3);
        team.setStatus("已满员");
        when(teamMapper.selectById(1L)).thenReturn(team);
        TeamMember member = new TeamMember();
        member.setId(10L);
        member.setTeamId(1L);
        member.setUserId(2L);
        member.setUserName("队员");
        member.setRole("队员");
        when(teamMemberMapper.selectOne(any())).thenReturn(member);

        teamService.leave(1L, 2L);

        verify(teamMemberMapper).deleteById(10L);
        assertEquals(2, team.getMemberCount());
        assertEquals("招募中", team.getStatus());
        verify(notificationService).send(eq(1L), eq("LEAVE"), any(String.class));
    }

    @Test
    @DisplayName("退出队伍：队长不可退出")
    void leave_captainBlocked() {
        when(teamMapper.selectById(1L)).thenReturn(team);
        TeamMember member = new TeamMember();
        member.setRole("队长");
        when(teamMemberMapper.selectOne(any())).thenReturn(member);

        assertThrows(BusinessException.class, () -> teamService.leave(1L, 1L));
        verify(teamMemberMapper, never()).deleteById(any(Long.class));
    }

    // ===== 修改队名 =====

    @Test
    @DisplayName("修改队名：队长可改并返回新名")
    void renameTeam_happyPath() {
        when(teamMapper.selectById(1L)).thenReturn(team);
        when(teamMemberMapper.selectList(any())).thenReturn(List.of());

        TeamVO vo = teamService.renameTeam(1L, "新队名", 1L);

        assertEquals("新队名", vo.getTitle());
        assertEquals("新队名", team.getTitle());
    }

    @Test
    @DisplayName("修改队名：非队长被拒绝")
    void renameTeam_notCaptain() {
        when(teamMapper.selectById(1L)).thenReturn(team);

        assertThrows(BusinessException.class, () -> teamService.renameTeam(1L, "新队名", 2L));
        verify(teamMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("修改队名：空名被拒绝")
    void renameTeam_blankTitle() {
        when(teamMapper.selectById(1L)).thenReturn(team);

        assertThrows(BusinessException.class, () -> teamService.renameTeam(1L, "   ", 1L));
        verify(teamMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("解散队伍：仅队长可操作")
    void disband_notCaptain() {
        when(teamMapper.selectById(1L)).thenReturn(team);

        assertThrows(BusinessException.class, () -> teamService.disband(1L, 2L));
        verify(teamMapper, never()).deleteById(any(Long.class));
    }

    private TeamApplication pendingInvite(Long teamId, Long userId) {
        TeamApplication app = new TeamApplication();
        app.setId(1L);
        app.setTeamId(teamId);
        app.setUserId(userId);
        app.setUserName("队员");
        app.setStatus("待审批");
        app.setInvited(true);
        return app;
    }
}
