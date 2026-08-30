package com.campus.competition.controller;

import com.campus.competition.common.PageVO;
import com.campus.competition.common.Result;
import com.campus.competition.common.UserContext;
import com.campus.competition.dto.AchievementReq;
import com.campus.competition.annotation.OpLog;
import com.campus.competition.service.AchievementService;
import com.campus.competition.vo.AchievementVO;
import org.springframework.security.access.prepost.PreAuthorize;
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
 * 成果接口（v9.0：发布需证明待审核；公开仅已通过；删除仅管理员）
 */
@RestController
@RequestMapping("/api/achievements")
public class AchievementController {

    private final AchievementService achievementService;

    public AchievementController(AchievementService achievementService) {
        this.achievementService = achievementService;
    }

    /** 公开成果墙（仅已通过） */
    @GetMapping
    public Result<PageVO<AchievementVO>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "9") long size) {
        return Result.success(achievementService.list(page, size));
    }

    /** 提交成果（带证明，进入待审核） */
    @OpLog("发布成果（待审核）")
    @PostMapping
    public Result<AchievementVO> create(@Valid @RequestBody AchievementReq req) {
        return Result.success(achievementService.create(UserContext.getUserId(), req));
    }

    /** 我的成果（含审核状态） */
    @GetMapping("/my")
    public Result<PageVO<AchievementVO>> my(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.success(achievementService.myList(UserContext.getUserId(), page, size));
    }

    /** 待审核成果数量（管理员红点） */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/pending-count")
    public Result<Long> pendingCount() {
        return Result.success(achievementService.pendingCount());
    }

    /** 管理端：成果审核列表 */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin")
    public Result<PageVO<AchievementVO>> adminList(@RequestParam(required = false) String status,
                                                   @RequestParam(defaultValue = "1") long page,
                                                   @RequestParam(defaultValue = "10") long size) {
        return Result.success(achievementService.adminList(status, page, size));
    }

    /** 管理端：审核通过 */
    @PreAuthorize("hasRole('ADMIN')")
    @OpLog("成果审核通过")
    @PostMapping("/admin/{id}/approve")
    public Result<Void> approve(@PathVariable Long id) {
        achievementService.approve(id, UserContext.getUserId());
        return Result.success();
    }

    /** 管理端：审核拒绝 */
    @PreAuthorize("hasRole('ADMIN')")
    @OpLog("成果审核拒绝")
    @PostMapping("/admin/{id}/reject")
    public Result<Void> reject(@PathVariable Long id) {
        achievementService.reject(id, UserContext.getUserId());
        return Result.success();
    }

    /** 删除：仅管理员 */
    @PreAuthorize("hasRole('ADMIN')")
    @OpLog("删除成果")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        achievementService.delete(id, UserContext.getUserId());
        return Result.success();
    }
}
