package com.campus.competition.controller;

import com.campus.competition.common.Result;
import com.campus.competition.common.UserContext;
import com.campus.competition.dto.FeedbackReq;
import com.campus.competition.entity.Feedback;
import com.campus.competition.annotation.OpLog;
import com.campus.competition.service.FeedbackService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/**
 * 赛事/功能反馈接口：学生提交，管理员处理
 */
@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @OpLog("提交反馈")
    @PostMapping
    public Result<Feedback> submit(@Valid @RequestBody FeedbackReq req) {
        return Result.success(feedbackService.submit(UserContext.getUserId(), req));
    }

    @GetMapping("/my")
    public Result<com.campus.competition.common.PageVO<Feedback>> my(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.success(feedbackService.myFeedback(UserContext.getUserId(), page, size));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public Result<com.campus.competition.common.PageVO<Feedback>> listAll(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.success(feedbackService.listAll(UserContext.getUserId(), page, size));
    }

    /** 未处理反馈数量（管理员红点） */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/pending-count")
    public Result<Long> pendingCount() {
        return Result.success(feedbackService.pendingCount());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @OpLog("处理反馈")
    @PostMapping("/{id}/handle")
    public Result<Void> handle(@PathVariable Long id) {
        feedbackService.handle(id, UserContext.getUserId());
        return Result.success();
    }
}
