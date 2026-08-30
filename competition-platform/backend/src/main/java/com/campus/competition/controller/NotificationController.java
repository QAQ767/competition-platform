package com.campus.competition.controller;

import com.campus.competition.common.Result;
import com.campus.competition.common.UserContext;
import com.campus.competition.entity.Notification;
import com.campus.competition.service.NotificationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 消息通知接口
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public Result<List<Notification>> list() {
        return Result.success(notificationService.list(UserContext.getUserId()));
    }

    @PostMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable Long id) {
        notificationService.markRead(id, UserContext.getUserId());
        return Result.success();
    }

    /** 全部标记为已读（清空铃铛未读角标） */
    @PostMapping("/clear")
    public Result<Void> clearAll() {
        notificationService.clearAll(UserContext.getUserId());
        return Result.success();
    }
}
