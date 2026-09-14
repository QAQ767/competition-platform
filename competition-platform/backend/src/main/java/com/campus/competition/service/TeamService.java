package com.campus.competition.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.competition.common.BusinessException;
import com.campus.competition.common.PageVO;
import com.campus.competition.dto.ApplyReq;
import com.campus.competition.dto.TeamCreateReq;
import com.campus.competition.entity.Competition;
import com.campus.competition.entity.Team;
import com.campus.competition.entity.TeamApplication;
import com.campus.competition.entity.TeamExitLog;
import com.campus.competition.entity.TeamMember;
import com.campus.competition.entity.User;
import com.campus.competition.mapper.CompetitionMapper;
import com.campus.competition.mapper.TeamApplicationMapper;
import com.campus.competition.mapper.TeamExitLogMapper;
import com.campus.competition.mapper.TeamMapper;
import com.campus.competition.mapper.TeamMemberMapper;
import com.campus.competition.mapper.UserMapper;
import com.campus.competition.support.TeamVOHelper;
import com.campus.competition.vo.InviteVO;
import com.campus.competition.vo.TeamDetailVO;
import com.campus.competition.vo.TeamVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 队伍服务：组队广场 / 创建 / 申请 / 审批 / 邀请
 */
@Service
public class TeamService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FMT_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final TeamMapper teamMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final TeamApplicationMapper teamApplicationMapper;
    private final TeamExitLogMapper teamExitLogMapper;
    private final CompetitionMapper competitionMapper;
    private final UserMapper userMapper;
    private final NotificationService notificationService;

    public TeamService(TeamMapper teamMapper, TeamMemberMapper teamMemberMapper,
                       TeamApplicationMapper teamApplicationMapper, TeamExitLogMapper teamExitLogMapper,
                       CompetitionMapper competitionMapper,
                       UserMapper userMapper, NotificationService notificationService) {
        this.teamMapper = teamMapper;
        this.teamMemberMapper = teamMemberMapper;
        this.teamApplicationMapper = teamApplicationMapper;
        this.teamExitLogMapper = teamExitLogMapper;
        this.competitionMapper = competitionMapper;
        this.userMapper = userMapper;
        this.notificationService = notificationService;
    }

    public PageVO<TeamVO> list(Long competitionId, String skill, String status, String keyword,
                               long page, long size) {
        LambdaQueryWrapper<Team> qw = new LambdaQueryWrapper<>();
        if (competitionId != null) {
            qw.eq(Team::getCompetitionId, competitionId);
        }
        if (status != null && !status.isBlank()) {
            qw.eq(Team::getStatus, status);
        }
        if (keyword != null && !keyword.isBlank()) {
            qw.like(Team::getTitle, keyword);
        }
        if (skill != null && !skill.isBlank()) {
            qw.like(Team::getSkills, skill);
        }
        qw.orderByDesc(Team::getCreatedAt);
        Page<Team> result = teamMapper.selectPage(new Page<>(page, size), qw);
        List<TeamVO> records = result.getRecords().stream()
                .map(t -> TeamVOHelper.toTeamVO(t, competitionMapper, userMapper))
                .collect(Collectors.toList());
        return new PageVO<>(records, result.getTotal(), result.getCurrent(), result.getSize(), result.getPages());
    }

    public TeamDetailVO detail(Long id) {
        Team team = teamMapper.selectById(id);
        if (team == null) {
            throw new BusinessException("队伍不存在");
        }
        TeamDetailVO vo = new TeamDetailVO();
        vo.setTeam(TeamVOHelper.toTeamVO(team, competitionMapper, userMapper));
        vo.setMembers(teamMemberMapper.selectList(
                new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getTeamId, id)
                        .orderByAsc(TeamMember::getJoinedTime)));
        vo.setApplications(teamApplicationMapper.selectList(
                new LambdaQueryWrapper<TeamApplication>()
                        .eq(TeamApplication::getTeamId, id)
                        .orderByDesc(TeamApplication::getApplyTime)));
        return vo;
    }

    @Transactional
    public TeamVO create(Long userId, TeamCreateReq req) {
        Competition competition = competitionMapper.selectById(req.getCompetitionId());
        if (competition == null) {
            throw new BusinessException("目标竞赛不存在");
        }
        Team team = new Team();
        team.setCompetitionId(req.getCompetitionId());
        team.setCaptainId(userId);
        team.setTitle(req.getTitle());
        team.setDescription(req.getDescription());
        team.setMaxMembers(req.getMaxMembers() == null ? 3 : req.getMaxMembers());
        team.setMemberCount(1);
        team.setStatus("招募中");
        team.setSkills(req.getSkills() == null ? "" : String.join(",", req.getSkills()));
        team.setDeadline(parse(req.getDeadline()));
        team.setCreatedAt(LocalDateTime.now());
        teamMapper.insert(team);

        User captain = userMapper.selectById(userId);
        TeamMember member = new TeamMember();
        member.setTeamId(team.getId());
        member.setUserId(userId);
        member.setUserName(captain == null ? "未知" : captain.getNickname());
        member.setRole("队长");
        member.setJoinedTime(LocalDateTime.now());
        teamMemberMapper.insert(member);
        return TeamVOHelper.toTeamVO(team, competitionMapper, userMapper);
    }

    @Transactional
    public TeamApplication apply(Long teamId, Long userId, ApplyReq req) {
        Team team = requireTeam(teamId);
        checkRecruitmentOpen(team);
        if (team.getCaptainId().equals(userId)) {
            throw new BusinessException("你是队长，无需申请");
        }
        if (team.getMemberCount() >= team.getMaxMembers()) {
            throw new BusinessException("队伍已满员");
        }
        Long memberCount = teamMemberMapper.selectCount(
                new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getTeamId, teamId)
                        .eq(TeamMember::getUserId, userId));
        if (memberCount > 0) {
            throw new BusinessException("你已是该队伍成员");
        }
        Long pending = teamApplicationMapper.selectCount(
                new LambdaQueryWrapper<TeamApplication>()
                        .eq(TeamApplication::getTeamId, teamId)
                        .eq(TeamApplication::getUserId, userId)
                        .eq(TeamApplication::getStatus, "待审批"));
        if (pending > 0) {
            throw new BusinessException("你已申请过该队伍，请等待队长审批");
        }

        User applicant = userMapper.selectById(userId);
        TeamApplication application = new TeamApplication();
        application.setTeamId(teamId);
        application.setUserId(userId);
        application.setUserName(applicant == null ? "未知" : applicant.getNickname());
        application.setIntro(req == null ? null : req.getIntro());
        application.setStatus("待审批");
        application.setInvited(false);
        application.setApplyTime(LocalDateTime.now());
        teamApplicationMapper.insert(application);

        notificationService.send(team.getCaptainId(), "APPLY",
                (applicant == null ? "有人" : applicant.getNickname()) + " 申请加入你的队伍「" + team.getTitle() + "」");
        return application;
    }

    @Transactional
    public void approve(Long teamId, Long appId, Long operatorId) {
        Team team = requireTeam(teamId);
        checkCaptain(team, operatorId);
        checkRecruitmentOpen(team);
        TeamApplication application = requireApplication(appId, teamId);
        if (!"待审批".equals(application.getStatus())) {
            throw new BusinessException("该申请已处理");
        }
        if (team.getMemberCount() >= team.getMaxMembers()) {
            throw new BusinessException("队伍已满员");
        }
        application.setStatus("已通过");
        teamApplicationMapper.updateById(application);

        TeamMember member = new TeamMember();
        member.setTeamId(teamId);
        member.setUserId(application.getUserId());
        member.setUserName(application.getUserName());
        member.setRole("队员");
        member.setJoinedTime(LocalDateTime.now());
        teamMemberMapper.insert(member);

        team.setMemberCount(team.getMemberCount() + 1);
        if (team.getMemberCount() >= team.getMaxMembers()) {
            team.setStatus("已满员");
        }
        teamMapper.updateById(team);
        notificationService.send(application.getUserId(), "APPROVE",
                "你申请加入的队伍「" + team.getTitle() + "」已通过审核，快去看看吧");
    }

    @Transactional
    public void reject(Long teamId, Long appId, Long operatorId) {
        Team team = requireTeam(teamId);
        checkCaptain(team, operatorId);
        TeamApplication application = requireApplication(appId, teamId);
        if (!"待审批".equals(application.getStatus())) {
            throw new BusinessException("该申请已处理");
        }
        application.setStatus("已拒绝");
        teamApplicationMapper.updateById(application);
        notificationService.send(application.getUserId(), "REJECT",
                "很遗憾，你申请加入的队伍「" + team.getTitle() + "」未通过审批");
    }

    /**
     * 队长邀请用户入队：生成一条「待审批 + invited=true」的邀请记录并通知对方，
     * 被邀请人可通过 GET /teams/invites/me 查看并通过 accept/reject 处理。
     */
    @Transactional
    public void invite(Long teamId, Long targetUserId, Long operatorId) {
        Team team = requireTeam(teamId);
        checkCaptain(team, operatorId);
        checkRecruitmentOpen(team);
        User captain = userMapper.selectById(operatorId);
        User target = userMapper.selectById(targetUserId);
        if (target == null) {
            throw new BusinessException("被邀请用户不存在");
        }
        if (team.getMemberCount() >= team.getMaxMembers()) {
            throw new BusinessException("队伍已满员，无法邀请");
        }
        Long memberCount = teamMemberMapper.selectCount(
                new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getTeamId, teamId)
                        .eq(TeamMember::getUserId, targetUserId));
        if (memberCount > 0) {
            throw new BusinessException("对方已是该队伍成员");
        }
        Long pending = teamApplicationMapper.selectCount(
                new LambdaQueryWrapper<TeamApplication>()
                        .eq(TeamApplication::getTeamId, teamId)
                        .eq(TeamApplication::getUserId, targetUserId)
                        .eq(TeamApplication::getStatus, "待审批"));
        if (pending > 0) {
            throw new BusinessException("已向对方发送过邀请，请等待对方处理");
        }

        TeamApplication application = new TeamApplication();
        application.setTeamId(teamId);
        application.setUserId(targetUserId);
        application.setUserName(target.getNickname());
        application.setStatus("待审批");
        application.setInvited(true);
        application.setApplyTime(LocalDateTime.now());
        teamApplicationMapper.insert(application);

        notificationService.sendInvite(targetUserId, application.getId(),
                "队长「" + (captain == null ? "未知" : captain.getNickname())
                        + "」邀请你加入队伍「" + team.getTitle() + "」，点击消息铃铛可接受或拒绝");
    }

    /** 我收到的待处理邀请列表（含队伍信息） */
    public List<InviteVO> listMyInvites(Long userId) {
        List<TeamApplication> apps = teamApplicationMapper.selectList(
                new LambdaQueryWrapper<TeamApplication>()
                        .eq(TeamApplication::getUserId, userId)
                        .eq(TeamApplication::getInvited, true)
                        .eq(TeamApplication::getStatus, "待审批")
                        .orderByDesc(TeamApplication::getApplyTime));
        if (apps.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> teamIds = apps.stream().map(TeamApplication::getTeamId).distinct().collect(Collectors.toList());
        Map<Long, Team> teamMap = teamMapper.selectBatchIds(teamIds).stream()
                .collect(Collectors.toMap(Team::getId, t -> t));
        List<Long> competitionIds = teamMap.values().stream()
                .map(Team::getCompetitionId).filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, Competition> competitionMap = competitionIds.isEmpty() ? java.util.Collections.emptyMap()
                : competitionMapper.selectBatchIds(competitionIds).stream()
                        .collect(Collectors.toMap(Competition::getId, c -> c));
        List<Long> captainIds = teamMap.values().stream()
                .map(Team::getCaptainId).filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, User> captainMap = captainIds.isEmpty() ? java.util.Collections.emptyMap()
                : userMapper.selectBatchIds(captainIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u));

        List<InviteVO> result = new ArrayList<>();
        for (TeamApplication a : apps) {
            Team team = teamMap.get(a.getTeamId());
            if (team == null) {
                continue;
            }
            InviteVO vo = new InviteVO();
            vo.setId(a.getId());
            vo.setTeamId(team.getId());
            vo.setTeamTitle(team.getTitle());
            Competition competition = competitionMap.get(team.getCompetitionId());
            vo.setCompetitionName(competition == null ? null : competition.getName());
            User captain = captainMap.get(team.getCaptainId());
            vo.setCaptainName(captain == null ? null : captain.getNickname());
            vo.setMemberCount(team.getMemberCount());
            vo.setMaxMembers(team.getMaxMembers());
            vo.setApplyTime(a.getApplyTime());
            result.add(vo);
        }
        return result;
    }

    /** 被邀请人接受邀请，直接入队（邀请本身即队长的同意，无需再次审批） */
    @Transactional
    public void acceptInvite(Long appId, Long userId) {
        TeamApplication application = teamApplicationMapper.selectById(appId);
        if (application == null || !userId.equals(application.getUserId())) {
            throw new BusinessException("邀请不存在");
        }
        if (!Boolean.TRUE.equals(application.getInvited())) {
            throw new BusinessException("该记录不是邀请，请走申请流程");
        }
        if (!"待审批".equals(application.getStatus())) {
            throw new BusinessException("该邀请已处理");
        }
        Team team = requireTeam(application.getTeamId());
        checkRecruitmentOpen(team);
        application = teamApplicationMapper.selectForUpdate(appId);
        if (application == null || !"待审批".equals(application.getStatus())) {
            throw new BusinessException("该邀请已处理或已失效");
        }
        if (team.getMemberCount() >= team.getMaxMembers()) {
            throw new BusinessException("队伍已满员，无法加入");
        }
        Long already = teamMemberMapper.selectCount(
                new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getTeamId, team.getId())
                        .eq(TeamMember::getUserId, userId));
        if (already > 0) {
            throw new BusinessException("你已是该队伍成员");
        }

        application.setStatus("已通过");
        teamApplicationMapper.updateById(application);

        TeamMember member = new TeamMember();
        member.setTeamId(team.getId());
        member.setUserId(userId);
        member.setUserName(application.getUserName());
        member.setRole("队员");
        member.setJoinedTime(LocalDateTime.now());
        teamMemberMapper.insert(member);

        team.setMemberCount(team.getMemberCount() + 1);
        if (team.getMemberCount() >= team.getMaxMembers()) {
            team.setStatus("已满员");
        }
        teamMapper.updateById(team);

        notificationService.markInviteHandled(appId, userId);
        notificationService.send(team.getCaptainId(), "INVITE_ACCEPTED",
                application.getUserName() + " 接受了你的邀请，已加入队伍「" + team.getTitle() + "」");
    }

    /** 被邀请人拒绝邀请 */
    @Transactional
    public void rejectInvite(Long appId, Long userId) {
        TeamApplication application = teamApplicationMapper.selectById(appId);
        if (application == null || !userId.equals(application.getUserId())) {
            throw new BusinessException("邀请不存在");
        }
        if (!Boolean.TRUE.equals(application.getInvited())) {
            throw new BusinessException("该记录不是邀请");
        }
        if (!"待审批".equals(application.getStatus())) {
            throw new BusinessException("该邀请已处理");
        }
        Team team = requireTeam(application.getTeamId());
        application = teamApplicationMapper.selectForUpdate(appId);
        if (application == null || !"待审批".equals(application.getStatus())) {
            throw new BusinessException("该邀请已处理或已失效");
        }
        application.setStatus("已拒绝");
        teamApplicationMapper.updateById(application);
        notificationService.markInviteHandled(appId, userId);
        if (team != null) {
            notificationService.send(team.getCaptainId(), "INVITE_DECLINED",
                    application.getUserName() + " 拒绝了你的入队邀请（队伍「" + team.getTitle() + "」）");
        }
    }

    /**
     * 队长踢出队员（理由必填）
     */
    @Transactional
    public void kick(Long teamId, Long memberId, String reason, Long operatorId) {
        Team team = requireTeam(teamId);
        checkCaptain(team, operatorId);
        if (memberId.equals(operatorId)) {
            throw new BusinessException("不能移除队长自己");
        }
        TeamMember member = teamMemberMapper.selectOne(
                new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getTeamId, teamId)
                        .eq(TeamMember::getUserId, memberId));
        if (member == null) {
            throw new BusinessException("该用户不是本队成员");
        }
        if ("队长".equals(member.getRole())) {
            throw new BusinessException("不能移除队长");
        }

        teamMemberMapper.deleteById(member.getId());

        TeamExitLog log = new TeamExitLog();
        log.setTeamId(teamId);
        log.setTeamTitle(team.getTitle());
        log.setUserId(memberId);
        log.setUserName(member.getUserName());
        log.setType("KICK");
        log.setReason(reason);
        log.setOperatorId(operatorId);
        log.setCreateTime(LocalDateTime.now());
        teamExitLogMapper.insert(log);

        team.setMemberCount(team.getMemberCount() - 1);
        if ("已满员".equals(team.getStatus())) {
            team.setStatus(deadlinePassed(team) ? "已结束" : "招募中");
        }
        teamMapper.updateById(team);

        User captain = userMapper.selectById(operatorId);
        notificationService.send(memberId, "KICK",
                "你已被队长「" + (captain == null ? "未知" : captain.getNickname())
                        + "」移出队伍「" + team.getTitle() + "」，理由：" + reason);
    }

    /**
     * 队长修改队伍简介
     */
    @Transactional
    public TeamVO updateDescription(Long teamId, String description, Long operatorId) {
        Team team = requireTeam(teamId);
        checkCaptain(team, operatorId);
        String desc = description == null ? null : description.trim();
        if (desc != null && desc.length() > 1000) {
            throw new BusinessException("队伍简介最长 1000 字");
        }
        team.setDescription(desc);
        teamMapper.updateById(team);
        return TeamVOHelper.toTeamVO(team, competitionMapper, userMapper);
    }

    /**
     * 队长修改队伍名（通知全体成员）
     */
    @Transactional
    public TeamVO renameTeam(Long teamId, String newTitle, Long operatorId) {
        Team team = requireTeam(teamId);
        checkCaptain(team, operatorId);
        if (newTitle == null || newTitle.isBlank()) {
            throw new BusinessException("队伍名不能为空");
        }
        String title = newTitle.trim();
        if (title.length() > 50) {
            throw new BusinessException("队伍名最长 50 字");
        }
        if (title.equals(team.getTitle())) {
            return TeamVOHelper.toTeamVO(team, competitionMapper, userMapper);
        }
        team.setTitle(title);
        teamMapper.updateById(team);

        List<TeamMember> members = teamMemberMapper.selectList(
                new LambdaQueryWrapper<TeamMember>().eq(TeamMember::getTeamId, teamId));
        for (TeamMember m : members) {
            if (!m.getUserId().equals(operatorId)) {
                notificationService.send(m.getUserId(), "SYSTEM", "队长已将队伍改名为「" + title + "」");
            }
        }
        return TeamVOHelper.toTeamVO(team, competitionMapper, userMapper);
    }

    /**
     * 成员主动退出队伍（队长不可退出，需使用解散队伍）
     */
    @Transactional
    public void leave(Long teamId, Long userId) {
        Team team = requireTeam(teamId);
        TeamMember member = teamMemberMapper.selectOne(
                new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getTeamId, teamId)
                        .eq(TeamMember::getUserId, userId));
        if (member == null) {
            throw new BusinessException("你不在该队伍中");
        }
        if ("队长".equals(member.getRole())) {
            throw new BusinessException("你是队长，不能退出队伍（如需解散请使用解散队伍）");
        }

        teamMemberMapper.deleteById(member.getId());

        TeamExitLog log = new TeamExitLog();
        log.setTeamId(teamId);
        log.setTeamTitle(team.getTitle());
        log.setUserId(userId);
        log.setUserName(member.getUserName());
        log.setType("LEAVE");
        log.setReason("主动退出");
        log.setOperatorId(userId);
        log.setCreateTime(LocalDateTime.now());
        teamExitLogMapper.insert(log);

        team.setMemberCount(team.getMemberCount() - 1);
        if ("已满员".equals(team.getStatus())) {
            team.setStatus(deadlinePassed(team) ? "已结束" : "招募中");
        }
        teamMapper.updateById(team);

        User captain = userMapper.selectById(team.getCaptainId());
        notificationService.send(team.getCaptainId(), "LEAVE",
                member.getUserName() + " 已退出队伍「" + team.getTitle() + "」");
    }

    /**
     * 队长解散队伍：通知全体成员并清理队伍相关数据
     */
    @Transactional
    public void disband(Long teamId, Long operatorId) {
        Team team = requireTeam(teamId);
        checkCaptain(team, operatorId);
        User captain = userMapper.selectById(operatorId);

        List<TeamMember> members = teamMemberMapper.selectList(
                new LambdaQueryWrapper<TeamMember>().eq(TeamMember::getTeamId, teamId));
        for (TeamMember m : members) {
            TeamExitLog log = new TeamExitLog();
            log.setTeamId(teamId);
            log.setTeamTitle(team.getTitle());
            log.setUserId(m.getUserId());
            log.setUserName(m.getUserName());
            log.setType("DISBAND");
            log.setReason("队伍被队长解散");
            log.setOperatorId(operatorId);
            log.setCreateTime(LocalDateTime.now());
            teamExitLogMapper.insert(log);

            if (!m.getUserId().equals(operatorId)) {
                notificationService.send(m.getUserId(), "DISBAND",
                        "队伍「" + team.getTitle() + "」已被队长"
                                + (captain == null ? "" : "「" + captain.getNickname() + "」") + "解散");
            }
        }
        teamApplicationMapper.delete(
                new LambdaQueryWrapper<TeamApplication>().eq(TeamApplication::getTeamId, teamId));
        teamMemberMapper.delete(
                new LambdaQueryWrapper<TeamMember>().eq(TeamMember::getTeamId, teamId));
        teamMapper.deleteById(teamId);
    }

    private boolean deadlinePassed(Team team) {
        return team.getDeadline() != null && !team.getDeadline().isAfter(LocalDateTime.now());
    }

    private void checkRecruitmentOpen(Team team) {
        if ("已结束".equals(team.getStatus()) || deadlinePassed(team)) {
            throw new BusinessException("队伍已结束或已到组队截止时间，无法继续招募");
        }
    }

    private Team requireTeam(Long teamId) {
        Team team = teamMapper.selectForUpdate(teamId);
        if (team == null) {
            throw new BusinessException("队伍不存在");
        }
        return team;
    }

    private void checkCaptain(Team team, Long operatorId) {
        if (!team.getCaptainId().equals(operatorId)) {
            throw new BusinessException("只有队长可以执行该操作");
        }
    }

    private TeamApplication requireApplication(Long appId, Long teamId) {
        TeamApplication application = teamApplicationMapper.selectById(appId);
        if (application == null || !application.getTeamId().equals(teamId)) {
            throw new BusinessException("申请不存在");
        }
        return application;
    }

    private LocalDateTime parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(text, FMT);
        } catch (Exception e) {
            return java.time.LocalDate.parse(text, FMT_DATE).atStartOfDay();
        }
    }
}
