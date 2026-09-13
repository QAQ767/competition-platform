package com.campus.competition.controller;

import com.campus.competition.common.Result;
import com.campus.competition.common.UserContext;
import com.campus.competition.dto.TaskCreateReq;
import com.campus.competition.dto.TaskStatusReq;
import com.campus.competition.dto.PreparationPlanReq;
import com.campus.competition.entity.TeamTask;
import com.campus.competition.annotation.OpLog;
import com.campus.competition.service.TeamTaskService;
import com.campus.competition.vo.TeamTaskVO;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/**
 * 任务分工接口
 */
@RestController
@RequestMapping("/api")
public class TaskController {

    private final TeamTaskService teamTaskService;

    public TaskController(TeamTaskService teamTaskService) {
        this.teamTaskService = teamTaskService;
    }

    @GetMapping("/teams/{id}/tasks")
    public Result<List<TeamTaskVO>> list(@PathVariable Long id) {
        return Result.success(teamTaskService.list(id, UserContext.getUserId()));
    }

    @OpLog("新建任务")
    @PostMapping("/teams/{id}/tasks")
    public Result<TeamTask> create(@PathVariable Long id, @Valid @RequestBody TaskCreateReq req) {
        return Result.success(teamTaskService.create(id, UserContext.getUserId(), req));
    }

    @OpLog("更新任务状态")
    @PutMapping("/tasks/{taskId}/status")
    public Result<Void> updateStatus(@PathVariable Long taskId, @Valid @RequestBody TaskStatusReq req) {
        teamTaskService.updateStatus(taskId, UserContext.getUserId(), req.getStatus(), req.getCompletionNote());
        return Result.success();
    }

    @OpLog("编辑备赛任务")
    @PutMapping("/tasks/{taskId}")
    public Result<Void> update(@PathVariable Long taskId, @Valid @RequestBody TaskCreateReq req) {
        teamTaskService.update(taskId, UserContext.getUserId(), req);
        return Result.success();
    }

    @OpLog("生成备赛计划")
    @PostMapping("/teams/{id}/tasks/plan")
    public Result<List<TeamTask>> generatePlan(@PathVariable Long id, @RequestBody PreparationPlanReq req) {
        return Result.success(teamTaskService.generatePlan(id, UserContext.getUserId(), req.getTargetDate()));
    }

    @OpLog("删除任务")
    @DeleteMapping("/tasks/{taskId}")
    public Result<Void> delete(@PathVariable Long taskId) {
        teamTaskService.delete(taskId, UserContext.getUserId());
        return Result.success();
    }
}
