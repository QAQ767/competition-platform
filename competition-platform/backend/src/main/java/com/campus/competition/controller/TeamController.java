package com.campus.competition.controller;

import com.campus.competition.common.Result;
import com.campus.competition.common.UserContext;
import com.campus.competition.dto.ApplyReq;
import com.campus.competition.dto.InviteReq;
import com.campus.competition.dto.KickReq;
import com.campus.competition.dto.TeamCreateReq;
import com.campus.competition.dto.TeamDescReq;
import com.campus.competition.dto.TeamRenameReq;
import com.campus.competition.entity.TeamApplication;
import com.campus.competition.annotation.OpLog;
import com.campus.competition.service.TeamService;
import com.campus.competition.vo.InviteVO;
import com.campus.competition.vo.TeamDetailVO;
import com.campus.competition.vo.TeamVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/**
 * 队伍接口
 */
@RestController
@RequestMapping("/api/teams")
public class TeamController {

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @GetMapping
    public Result<com.campus.competition.common.PageVO<TeamVO>> list(@RequestParam(required = false) Long competitionId,
                                                                     @RequestParam(required = false) String skill,
                                                                     @RequestParam(required = false) String status,
                                                                     @RequestParam(required = false) String keyword,
                                                                     @RequestParam(defaultValue = "1") long page,
                                                                     @RequestParam(defaultValue = "9") long size) {
        return Result.success(teamService.list(competitionId, skill, status, keyword, page, size));
    }

    @GetMapping("/{id}")
    public Result<TeamDetailVO> detail(@PathVariable Long id) {
        return Result.success(teamService.detail(id));
    }

    @OpLog("创建队伍")
    @PostMapping
    public Result<TeamVO> create(@RequestBody TeamCreateReq req) {
        return Result.success(teamService.create(UserContext.getUserId(), req));
    }

    @OpLog("申请入队")
    @PostMapping("/{id}/apply")
    public Result<TeamApplication> apply(@PathVariable Long id, @RequestBody(required = false) ApplyReq req) {
        return Result.success(teamService.apply(id, UserContext.getUserId(), req));
    }

    @OpLog("审批通过申请")
    @PostMapping("/{id}/applications/{appId}/approve")
    public Result<Void> approve(@PathVariable Long id, @PathVariable Long appId) {
        teamService.approve(id, appId, UserContext.getUserId());
        return Result.success();
    }

    @OpLog("拒绝申请")
    @PostMapping("/{id}/applications/{appId}/reject")
    public Result<Void> reject(@PathVariable Long id, @PathVariable Long appId) {
        teamService.reject(id, appId, UserContext.getUserId());
        return Result.success();
    }

    @OpLog("邀请入队")
    @PostMapping("/{id}/invite")
    public Result<Void> invite(@PathVariable Long id, @RequestBody InviteReq req) {
        teamService.invite(id, req.getUserId(), UserContext.getUserId());
        return Result.success();
    }

    /** 我收到的待处理邀请（被邀请人视角） */
    @GetMapping("/invites/me")
    public Result<List<InviteVO>> myInvites() {
        return Result.success(teamService.listMyInvites(UserContext.getUserId()));
    }

    @OpLog("接受入队邀请")
    @PostMapping("/invites/{appId}/accept")
    public Result<Void> acceptInvite(@PathVariable Long appId) {
        teamService.acceptInvite(appId, UserContext.getUserId());
        return Result.success();
    }

    @OpLog("拒绝入队邀请")
    @PostMapping("/invites/{appId}/reject")
    public Result<Void> rejectInvite(@PathVariable Long appId) {
        teamService.rejectInvite(appId, UserContext.getUserId());
        return Result.success();
    }

    @OpLog("踢出队员")
    @PostMapping("/{id}/members/{memberId}/kick")
    public Result<Void> kick(@PathVariable Long id, @PathVariable Long memberId,
                             @RequestBody @javax.validation.Valid KickReq req) {
        teamService.kick(id, memberId, req.getReason(), UserContext.getUserId());
        return Result.success();
    }

    @OpLog("解散队伍")
    @PostMapping("/{id}/disband")
    public Result<Void> disband(@PathVariable Long id) {
        teamService.disband(id, UserContext.getUserId());
        return Result.success();
    }

    @OpLog("退出队伍")
    @PostMapping("/{id}/leave")
    public Result<Void> leave(@PathVariable Long id) {
        teamService.leave(id, UserContext.getUserId());
        return Result.success();
    }

    @OpLog("修改队伍名")
    @PutMapping("/{id}/title")
    public Result<TeamVO> renameTeam(@PathVariable Long id, @RequestBody @Valid TeamRenameReq req) {
        return Result.success(teamService.renameTeam(id, req.getTitle(), UserContext.getUserId()));
    }

    @OpLog("修改队伍简介")
    @PutMapping("/{id}/description")
    public Result<TeamVO> updateDescription(@PathVariable Long id, @RequestBody TeamDescReq req) {
        return Result.success(teamService.updateDescription(id, req.getDescription(), UserContext.getUserId()));
    }
}
