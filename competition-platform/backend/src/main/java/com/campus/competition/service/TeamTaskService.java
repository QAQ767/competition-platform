package com.campus.competition.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.competition.common.BusinessException;
import com.campus.competition.dto.TaskCreateReq;
import com.campus.competition.entity.Team;
import com.campus.competition.entity.TeamTask;
import com.campus.competition.entity.User;
import com.campus.competition.mapper.TeamMapper;
import com.campus.competition.mapper.TeamMemberMapper;
import com.campus.competition.mapper.TeamTaskMapper;
import com.campus.competition.mapper.UserMapper;
import com.campus.competition.vo.TeamTaskVO;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 任务分工服务（队长建任务，成员更新状态）
 */
@Service
public class TeamTaskService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FMT_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final TeamTaskMapper teamTaskMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final TeamMapper teamMapper;
    private final UserMapper userMapper;
    private final ResourceService resourceService;

    public TeamTaskService(TeamTaskMapper teamTaskMapper, TeamMemberMapper teamMemberMapper,
                           TeamMapper teamMapper, UserMapper userMapper, ResourceService resourceService) {
        this.teamTaskMapper = teamTaskMapper;
        this.teamMemberMapper = teamMemberMapper;
        this.teamMapper = teamMapper;
        this.userMapper = userMapper;
        this.resourceService = resourceService;
    }

    public List<TeamTaskVO> list(Long teamId, Long userId) {
        requireMember(teamId, userId);
        return teamTaskMapper.selectList(
                        new LambdaQueryWrapper<TeamTask>()
                                .eq(TeamTask::getTeamId, teamId)
                                .orderByAsc(TeamTask::getId))
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    public TeamTask create(Long teamId, Long userId, TaskCreateReq req) {
        Team team = requireTeam(teamId);
        checkCaptain(team, userId);
        TeamTask task = new TeamTask();
        task.setTeamId(teamId);
        task.setTitle(req.getTitle());
        task.setContent(req.getContent());
        task.setAssigneeId(req.getAssigneeId());
        task.setStatus("待完成");
        task.setDeadline(parse(req.getDeadline()));
        task.setCreatedAt(LocalDateTime.now());
        teamTaskMapper.insert(task);
        return task;
    }

    public void updateStatus(Long taskId, Long userId, String status) {
        if (!List.of("待完成", "进行中", "已完成").contains(status)) {
            throw new BusinessException("非法的任务状态: " + status);
        }
        TeamTask task = teamTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("任务不存在");
        }
        Team team = requireTeam(task.getTeamId());
        boolean isAssignee = task.getAssigneeId() != null && task.getAssigneeId().equals(userId);
        boolean isCaptain = team.getCaptainId().equals(userId);
        if (!isAssignee && !isCaptain) {
            throw new BusinessException("仅负责人或队长可更新任务状态");
        }
        task.setStatus(status);
        teamTaskMapper.updateById(task);
    }

    public void delete(Long taskId, Long userId) {
        TeamTask task = teamTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("任务不存在");
        }
        Team team = requireTeam(task.getTeamId());
        checkCaptain(team, userId);
        teamTaskMapper.deleteById(taskId);
    }

    private void requireMember(Long teamId, Long userId) {
        if (teamMapper.selectById(teamId) == null) {
            throw new BusinessException("队伍不存在");
        }
        if (!resourceService.isMember(teamId, userId)) {
            throw new BusinessException("仅队伍成员可查看任务");
        }
    }

    private Team requireTeam(Long teamId) {
        Team team = teamMapper.selectById(teamId);
        if (team == null) {
            throw new BusinessException("队伍不存在");
        }
        return team;
    }

    private void checkCaptain(Team team, Long userId) {
        if (!team.getCaptainId().equals(userId)) {
            throw new BusinessException("只有队长可以执行该操作");
        }
    }

    private TeamTaskVO toVO(TeamTask task) {
        TeamTaskVO vo = new TeamTaskVO();
        vo.setId(task.getId());
        vo.setTeamId(task.getTeamId());
        vo.setTitle(task.getTitle());
        vo.setContent(task.getContent());
        vo.setAssigneeId(task.getAssigneeId());
        vo.setStatus(task.getStatus());
        vo.setDeadline(task.getDeadline());
        vo.setCreatedAt(task.getCreatedAt());
        if (task.getAssigneeId() != null) {
            User assignee = userMapper.selectById(task.getAssigneeId());
            vo.setAssigneeName(assignee == null ? "未知" : assignee.getNickname());
        }
        return vo;
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
