package com.campus.competition.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 会话注册表 + 实时推送器
 * <p>
 * 从 ChatWebSocketHandler 中抽出会话管理，使业务服务（如通知/邀请）也能实时推送给在线用户，
 * 同时避免服务之间的循环依赖。
 */
@Component
public class WsPusher {

    private static final Logger log = LoggerFactory.getLogger(WsPusher.class);

    /** 未认证哨兵值（ConcurrentHashMap 不允许 null 值） */
    private static final Long AUTH_PENDING = 0L;

    private final ObjectMapper objectMapper;

    /** session -> 已认证的 userId（认证前为 AUTH_PENDING） */
    private final Map<WebSocketSession, Long> sessions = new ConcurrentHashMap<>();

    public WsPusher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void register(WebSocketSession session) {
        sessions.put(session, AUTH_PENDING);
    }

    public void authenticate(WebSocketSession session, Long userId) {
        sessions.put(session, userId);
    }

    public void remove(WebSocketSession session) {
        sessions.remove(session);
    }

    public Long userIdOf(WebSocketSession session) {
        return sessions.get(session);
    }

    public boolean isAuthenticated(WebSocketSession session) {
        Long userId = sessions.get(session);
        return userId != null && userId > 0;
    }

    /** 推送给指定用户的全部在线会话 */
    public void pushToUser(Long userId, Map<String, Object> payload) {
        if (userId == null) {
            return;
        }
        for (Map.Entry<WebSocketSession, Long> entry : sessions.entrySet()) {
            if (userId.equals(entry.getValue())) {
                sendJson(entry.getKey(), payload);
            }
        }
    }

    /** 广播给所有已认证会话 */
    public void broadcast(Map<String, Object> payload) {
        for (Map.Entry<WebSocketSession, Long> entry : sessions.entrySet()) {
            if (entry.getValue() != null && entry.getValue() > 0) {
                sendJson(entry.getKey(), payload);
            }
        }
    }

    public void sendJson(WebSocketSession session, Object payload) {
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
            }
        } catch (Exception e) {
            log.warn("WebSocket 发送失败", e);
            sessions.remove(session);
        }
    }
}
