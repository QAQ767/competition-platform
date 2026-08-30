package com.campus.competition.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.competition.common.BusinessException;
import com.campus.competition.entity.Notification;
import com.campus.competition.mapper.NotificationMapper;
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

    public NotificationService(NotificationMapper notificationMapper, WsPusher wsPusher) {
        this.notificationMapper = notificationMapper;
        this.wsPusher = wsPusher;
    }

    public void send(Long userId, String type, String content) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setContent(content);
        notification.setIsRead(false);
        notification.setCreateTime(LocalDateTime.now());
        notificationMapper.insert(notification);
        // 实时推送给在线用户（前端收到后刷新铃铛）
        wsPusher.pushToUser(userId, Map.of("type", "NOTIFY", "data", notification));
    }

    public List<Notification> list(Long userId) {
        return notificationMapper.selectList(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .orderByDesc(Notification::getCreateTime));
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
