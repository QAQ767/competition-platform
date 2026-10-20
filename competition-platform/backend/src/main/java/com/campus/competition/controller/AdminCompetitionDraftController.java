package com.campus.competition.controller;

import com.campus.competition.annotation.OpLog;
import com.campus.competition.common.PageVO;
import com.campus.competition.common.Result;
import com.campus.competition.common.UserContext;
import com.campus.competition.dto.DraftUpdateReq;
import com.campus.competition.entity.CompetitionDraft;
import com.campus.competition.service.CompetitionDraftService;
import com.campus.competition.vo.CompetitionDraftVO;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 赛事发现草稿的审核与幂等发布。 */
@RestController
@RequestMapping("/api/admin/competition-drafts")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCompetitionDraftController {

    private final CompetitionDraftService draftService;

    public AdminCompetitionDraftController(CompetitionDraftService draftService) {
        this.draftService = draftService;
    }

    @GetMapping
    public Result<PageVO<CompetitionDraft>> list(@RequestParam(required = false) String status,
                                                 @RequestParam(defaultValue = "1") long page,
                                                 @RequestParam(defaultValue = "10") long size) {
        return Result.success(draftService.list(status, page, size));
    }

    @GetMapping("/{id}")
    public Result<CompetitionDraftVO> detail(@PathVariable Long id) {
        return Result.success(draftService.detail(id));
    }

    @OpLog("编辑赛事草稿")
    @PutMapping("/{id}")
    public Result<CompetitionDraftVO> update(@PathVariable Long id, @RequestBody DraftUpdateReq req) {
        return Result.success(draftService.update(id, req, UserContext.getUserId()));
    }

    @OpLog("发布赛事草稿")
    @PostMapping("/{id}/publish")
    public Result<Long> publish(@PathVariable Long id,
                                @RequestParam(required = false) Integer version) {
        return Result.success(draftService.publish(id, version, UserContext.getUserId()));
    }
}
