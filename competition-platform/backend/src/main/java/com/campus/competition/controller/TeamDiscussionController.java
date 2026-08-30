package com.campus.competition.controller;

import com.campus.competition.common.PageVO;
import com.campus.competition.common.Result;
import com.campus.competition.common.UserContext;
import com.campus.competition.dto.ChatSendReq;
import com.campus.competition.entity.TeamDiscussion;
import com.campus.competition.annotation.OpLog;
import com.campus.competition.service.TeamDiscussionService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 团队讨论区接口
 */
@RestController
@RequestMapping("/api")
public class TeamDiscussionController {

    private final TeamDiscussionService teamDiscussionService;

    public TeamDiscussionController(TeamDiscussionService teamDiscussionService) {
        this.teamDiscussionService = teamDiscussionService;
    }

    @GetMapping("/teams/{id}/discussions")
    public Result<PageVO<TeamDiscussion>> list(@PathVariable Long id,
                                               @RequestParam(defaultValue = "1") long page,
                                               @RequestParam(defaultValue = "30") long size) {
        return Result.success(teamDiscussionService.list(id, UserContext.getUserId(), page, size));
    }

    @OpLog("发布讨论")
    @PostMapping("/teams/{id}/discussions")
    public Result<TeamDiscussion> post(@PathVariable Long id, @Valid @RequestBody ChatSendReq req) {
        return Result.success(teamDiscussionService.post(id, UserContext.getUserId(), req.getContent()));
    }

    @OpLog("删除讨论消息")
    @DeleteMapping("/discussions/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        teamDiscussionService.delete(id, UserContext.getUserId());
        return Result.success();
    }
}
