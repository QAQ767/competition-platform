package com.campus.competition.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.competition.common.BusinessException;
import com.campus.competition.entity.Notification;
import com.campus.competition.mapper.NotificationMapper;
import com.campus.competition.mapper.TeamApplicationMapper;
import com.campus.competition.entity.TeamApplication;
import com.campus.competition.websocket.WsPusher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 消息通知服务：持久化 + WebSocket 实时推送
 */
@Service
public class NotificationService {

    private final NotificationMapper notificationMapper;
    private final WsPusher wsPusher;
    private final TeamApplicationMapper applicationMapper;

    public NotificationService(NotificationMapper notificationMapper, WsPusher wsPusher, TeamApplicationMapper applicationMapper) {
        this.notificationMapper = notificationMapper;
        this.wsPusher = wsPusher;
        this.applicationMapper = applicationMapper;
    }

    public void send(Long userId, String type, String content) {
        send(userId, type, content, null);
    }

    public void sendInvite(Long userId, Long inviteId, String content) {
        send(userId, "INVITE", content, inviteId);
    }

    private void send(Long userId, String type, String content, Long inviteId) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setInviteId(inviteId);
        notification.setContent(content);
        notification.setIsRead(false);
        notification.setCreateTime(LocalDateTime.now());
        notificationMapper.insert(notification);
        // 实时推送给在线用户（前端收到后刷新铃铛）
        Runnable push = () -> wsPusher.pushToUser(userId, Map.of("type", "NOTIFY", "data", notification));
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override public void afterCommit() { push.run(); }
                    });
        } else {
            push.run();
        }
    }

    public List<Notification> list(Long userId) {
        List<Notification> notifications = notificationMapper.selectList(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .orderByDesc(Notification::getCreateTime));
        List<Long> ids = notifications.stream().map(Notification::getInviteId)
                .filter(java.util.Objects::nonNull).distinct().collect(java.util.stream.Collectors.toList());
        Map<Long, TeamApplication> invitations = ids.isEmpty() ? Map.of() : applicationMapper.selectBatchIds(ids).stream()
                .filter(a -> userId.equals(a.getUserId()) && Boolean.TRUE.equals(a.getInvited()))
                .collect(java.util.stream.Collectors.toMap(TeamApplication::getId, a -> a));
        for (Notification n : notifications) {
            if (n.getInviteId() == null) continue;
            TeamApplication invite = invitations.get(n.getInviteId());
            n.setInviteStatus(invite == null ? "已失效" : invite.getStatus());
        }
        return notifications;
    }

    public void markInviteHandled(Long inviteId, Long userId) {
        notificationMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Notification>()
                        .eq(Notification::getUserId, userId).eq(Notification::getInviteId, inviteId)
                        .eq(Notification::getType, "INVITE").set(Notification::getIsRead, true));
    }

    public void markRead(Long id, Long userId) {
        Notification notification = notificationMapper.selectById(id);
        if (notification == null || !notification.getUserId().equals(userId)) {
            throw new BusinessException("通知不存在");
        }
        notification.setIsRead(true);
        notificationMapper.updateById(notification);
    }

    /** 将当前用户的全部通知标记为已读（清空铃铛角标） */
    public void clearAll(Long userId) {
        notificationMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .set(Notification::getIsRead, true));
    }
}
