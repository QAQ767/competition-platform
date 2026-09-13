package com.campus.competition.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campus.competition.common.BusinessException;
import com.campus.competition.dto.TaskCreateReq;
import com.campus.competition.entity.*;
import com.campus.competition.mapper.*;
import com.campus.competition.vo.TeamTaskVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 团队备赛任务：阶段计划、分工、进度和完成复盘。 */
@Service
public class TeamTaskService {
    private static final List<String> PHASES = List.of("准备阶段", "专项训练", "模拟冲刺", "作品提交");
    private static final List<String> PRIORITIES = List.of("普通", "重要", "紧急");
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")
            .withResolverStyle(ResolverStyle.STRICT);
    private final TeamTaskMapper taskMapper;
    private final TeamMapper teamMapper;
    private final UserMapper userMapper;
    private final ResourceMapper resourceMapper;
    private final CompetitionMapper competitionMapper;
    private final ResourceService resourceService;
    private final TeamMemberMapper memberMapper;
    private final NotificationService notifications;

    public TeamTaskService(TeamTaskMapper taskMapper, TeamMapper teamMapper, UserMapper userMapper,
                           ResourceMapper resourceMapper, CompetitionMapper competitionMapper,
                           ResourceService resourceService, NotificationService notifications, TeamMemberMapper memberMapper) {
        this.taskMapper = taskMapper;
        this.teamMapper = teamMapper;
        this.userMapper = userMapper;
        this.resourceMapper = resourceMapper;
        this.competitionMapper = competitionMapper;
        this.resourceService = resourceService;
        this.notifications = notifications;
        this.memberMapper = memberMapper;
    }

    public List<TeamTaskVO> list(Long teamId, Long userId) {
        requireMember(teamId, userId);
        List<TeamTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<TeamTask>()
                .eq(TeamTask::getTeamId, teamId).orderByAsc(TeamTask::getId));
        Set<Long> userIds = tasks.stream().map(TeamTask::getAssigneeId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> resourceIds = tasks.stream().map(TeamTask::getResourceId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, User> users = userIds.isEmpty() ? Map.of() : userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, Resource> resources = resourceIds.isEmpty() ? Map.of() : resourceMapper.selectBatchIds(resourceIds).stream()
                .filter(r -> teamId.equals(r.getTeamId())).collect(Collectors.toMap(Resource::getId, Function.identity()));
        return tasks.stream().map(task -> toVO(task, users, resources)).collect(Collectors.toList());
    }

    @Transactional
    public TeamTask create(Long teamId, Long userId, TaskCreateReq req) {
        Team team = lockTeam(teamId);
        checkCaptain(team, userId);
        validate(teamId, req);
        TeamTask task = new TeamTask();
        task.setTeamId(teamId);
        apply(task, req);
        task.setStatus("待完成");
        task.setCreatedAt(LocalDateTime.now());
        taskMapper.insert(task);
        notifyAssignment(task, userId);
        return task;
    }

    @Transactional
    public void update(Long taskId, Long userId, TaskCreateReq req) {
        TeamTask task = lockTask(taskId);
        checkCaptain(lockTeam(task.getTeamId()), userId);
        validate(task.getTeamId(), req);
        Long previousAssignee = task.getAssigneeId();
        apply(task, req);
        taskMapper.update(null, new LambdaUpdateWrapper<TeamTask>().eq(TeamTask::getId, taskId)
                .set(TeamTask::getTitle, task.getTitle()).set(TeamTask::getContent, task.getContent())
                .set(TeamTask::getPhase, task.getPhase()).set(TeamTask::getPriority, task.getPriority())
                .set(TeamTask::getAssigneeId, task.getAssigneeId()).set(TeamTask::getResourceId, task.getResourceId())
                .set(TeamTask::getDeadline, task.getDeadline()));
        if (!Objects.equals(previousAssignee, task.getAssigneeId())) notifyAssignment(task, userId);
    }

    @Transactional
    public void updateStatus(Long taskId, Long userId, String status, String completionNote) {
        if (status == null || !List.of("待完成", "进行中", "已完成").contains(status))
            throw new BusinessException(400, "请选择有效的任务状态");
        if (completionNote != null && completionNote.length() > 1000)
            throw new BusinessException(400, "复盘不能超过1000字");
        TeamTask task = lockTask(taskId);
        if (userId == null || memberMapper.selectCurrentMember(task.getTeamId(), userId) == null)
            throw new BusinessException(403, "仅当前队伍成员可更新任务");
        Team team = lockTeam(task.getTeamId());
        if (!Objects.equals(task.getAssigneeId(), userId) && !Objects.equals(team.getCaptainId(), userId))
            throw new BusinessException(403, "仅当前负责人或队长可更新任务状态");
        if (status.equals(task.getStatus())) return;
        boolean completed = "已完成".equals(status);
        taskMapper.update(null, new LambdaUpdateWrapper<TeamTask>().eq(TeamTask::getId, taskId)
                .set(TeamTask::getStatus, status)
                .set(TeamTask::getCompletedAt, completed ? LocalDateTime.now() : null)
                .set(TeamTask::getCompletionNote, completed && completionNote != null ? completionNote.trim() : null));
        if (completed && !userId.equals(team.getCaptainId()))
            notifications.send(team.getCaptainId(), "TASK", "队伍「" + team.getTitle() + "」的任务「" + task.getTitle() + "」已完成，可在备赛计划查看复盘");
    }

    @Transactional
    public void delete(Long taskId, Long userId) {
        TeamTask task = lockTask(taskId);
        checkCaptain(lockTeam(task.getTeamId()), userId);
        taskMapper.deleteById(taskId);
    }

    /** 只为尚无任务的队伍生成模板，队伍行锁防止并发重复生成。 */
    @Transactional
    public List<TeamTask> generatePlan(Long teamId, Long userId, String targetDate) {
        Team team = lockTeam(teamId);
        checkCaptain(team, userId);
        if (taskMapper.selectCount(new LambdaQueryWrapper<TeamTask>().eq(TeamTask::getTeamId, teamId)) > 0)
            throw new BusinessException(409, "队伍已有任务，请直接编辑现有计划");
        LocalDateTime end = parse(targetDate);
        if (end == null && team.getCompetitionId() != null) {
            Competition competition = competitionMapper.selectById(team.getCompetitionId());
            if (competition != null) end = competition.getStartTime();
        }
        LocalDateTime now = LocalDateTime.now();
        if (end == null || end.isBefore(now.plusDays(1)))
            throw new BusinessException(400, "请设置至少一天后的备赛目标时间");
        long seconds = Duration.between(now, end).getSeconds();
        String[] titles = {"确认赛题方向与团队分工", "完成专项学习与训练", "开展模拟训练与团队复盘", "检查作品并完成提交"};
        String[] contents = {"阅读竞赛规则，确认提交要求，为成员分配角色。", "整理训练资料，围绕薄弱项练习并记录成果。", "按赛制进行模拟练习，梳理问题并完成修订。", "检查作品、文档和提交清单，预留审核与上传时间。"};
        List<TeamTask> tasks = new ArrayList<>();
        for (int i = 0; i < PHASES.size(); i++) {
            TeamTask task = new TeamTask();
            task.setTeamId(teamId);
            task.setTitle(titles[i]);
            task.setContent(contents[i]);
            task.setPhase(PHASES.get(i));
            task.setPriority(i == 3 ? "重要" : "普通");
            task.setStatus("待完成");
            task.setDeadline(now.plusSeconds(seconds * (i + 1) / 4).withNano(0));
            task.setCreatedAt(now);
            taskMapper.insert(task);
            tasks.add(task);
        }
        return tasks;
    }

    private void validate(Long teamId, TaskCreateReq req) {
        if (req.getTitle() == null || req.getTitle().isBlank() || req.getTitle().length() > 100)
            throw new BusinessException(400, "任务标题须为1至100字");
        if (req.getContent() != null && req.getContent().length() > 500)
            throw new BusinessException(400, "任务说明不能超过500字");
        if (req.getPhase() != null && !PHASES.contains(req.getPhase()))
            throw new BusinessException(400, "请选择有效的备赛阶段");
        if (req.getPriority() != null && !PRIORITIES.contains(req.getPriority()))
            throw new BusinessException(400, "请选择有效的优先级");
        if (req.getAssigneeId() != null && memberMapper.selectCurrentMember(teamId, req.getAssigneeId()) == null)
            throw new BusinessException(400, "负责人必须是当前队伍成员");
        if (req.getResourceId() != null) {
            Resource resource = resourceMapper.selectById(req.getResourceId());
            if (resource == null || !teamId.equals(resource.getTeamId()))
                throw new BusinessException(400, "只能关联本队伍的训练资料");
        }
        parse(req.getDeadline());
    }

    private void apply(TeamTask task, TaskCreateReq req) {
        task.setTitle(req.getTitle().trim());
        task.setContent(req.getContent());
        task.setAssigneeId(req.getAssigneeId());
        task.setDeadline(parse(req.getDeadline()));
        task.setPhase(req.getPhase() == null ? "准备阶段" : req.getPhase());
        task.setPriority(req.getPriority() == null ? "普通" : req.getPriority());
        task.setResourceId(req.getResourceId());
    }

    private void notifyAssignment(TeamTask task, Long operatorId) {
        if (task.getAssigneeId() != null && !task.getAssigneeId().equals(operatorId))
            notifications.send(task.getAssigneeId(), "TASK", "你有新的备赛任务「" + task.getTitle() + "」，请到队伍备赛计划查看");
    }

    private Team lockTeam(Long id) {
        Team team = teamMapper.selectForUpdate(id);
        if (team == null) throw new BusinessException(404, "队伍不存在");
        return team;
    }

    private TeamTask lockTask(Long id) {
        TeamTask task = taskMapper.selectById(id);
        if (task == null) throw new BusinessException(404, "任务不存在");
        lockTeam(task.getTeamId());
        task = taskMapper.selectForUpdate(id);
        if (task == null) throw new BusinessException(404, "任务不存在");
        return task;
    }

    private void requireMember(Long teamId, Long userId) {
        if (userId == null || !resourceService.isMember(teamId, userId))
            throw new BusinessException(403, "仅当前队伍成员可访问任务");
    }

    private void checkCaptain(Team team, Long userId) {
        if (userId == null || !userId.equals(team.getCaptainId()))
            throw new BusinessException(403, "只有队长可以执行该操作");
    }

    private TeamTaskVO toVO(TeamTask task, Map<Long, User> users, Map<Long, Resource> resources) {
        TeamTaskVO vo = new TeamTaskVO();
        org.springframework.beans.BeanUtils.copyProperties(task, vo);
        User user = task.getAssigneeId() == null ? null : users.get(task.getAssigneeId());
        vo.setAssigneeName(task.getAssigneeId() == null ? "待分配" : user == null ? "用户已注销" : user.getNickname());
        Resource resource = task.getResourceId() == null ? null : resources.get(task.getResourceId());
        if (resource != null) { vo.setResourceName(resource.getName()); vo.setResourceUrl(resource.getUrl()); }
        return vo;
    }

    private LocalDateTime parse(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            if (text.length() == 10) return LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE).atTime(23, 59, 59);
            return LocalDateTime.parse(text.replace('T', ' '), FMT);
        } catch (DateTimeParseException e) {
            throw new BusinessException(400, "日期格式无效，请重新选择时间");
        }
    }
}
