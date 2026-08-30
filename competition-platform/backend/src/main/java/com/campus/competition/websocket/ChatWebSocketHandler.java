package com.campus.competition.websocket;

import com.campus.competition.common.BusinessException;
import com.campus.competition.entity.ChatMessage;
import com.campus.competition.entity.TeamDiscussion;
import com.campus.competition.entity.TeamMember;
import com.campus.competition.mapper.TeamMemberMapper;
import com.campus.competition.security.JwtService;
import com.campus.competition.service.ChatService;
import com.campus.competition.service.TeamDiscussionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;

/**
 * 聊天 WebSocket 处理器（正式版：服务端实时推送）
 *
 * 协议（JSON）：
 * 客户端 -> 服务端：
 *   {"type":"AUTH","token":"..."}                      首次连接认证
 *   {"type":"HALL","content":"..."}                    发大厅消息
 *   {"type":"DM","toUserId":3,"content":"..."}         发私聊消息
 *   {"type":"TEAM","teamId":1,"content":"..."}         发队伍讨论
 * 服务端 -> 客户端：
 *   {"type":"AUTH_OK"} / {"type":"AUTH_FAIL"}
 *   {"type":"HALL","data":{ChatMessage}}
 *   {"type":"DM","data":{ChatMessage}}
 *   {"type":"TEAM","data":{TeamDiscussion}}
 *   {"type":"NOTIFY","data":{Notification}}            业务通知（邀请/审批等）
 *
 * 说明：消息先持久化（复用 ChatService），再实时广播；未连接 WS 时前端自动降级为 REST 轮询。
 * 会话注册表与推送逻辑见 {@link WsPusher}（业务服务也可复用，例如邀请实时通知）。
 */
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);

    private final ObjectMapper objectMapper;
    private final JwtService jwtService;
    private final ChatService chatService;
    private final TeamDiscussionService teamDiscussionService;
    private final TeamMemberMapper teamMemberMapper;
    private final WsPusher wsPusher;

    public ChatWebSocketHandler(ObjectMapper objectMapper, JwtService jwtService, ChatService chatService,
                                TeamDiscussionService teamDiscussionService, TeamMemberMapper teamMemberMapper,
                                WsPusher wsPusher) {
        this.objectMapper = objectMapper;
        this.jwtService = jwtService;
        this.chatService = chatService;
        this.teamDiscussionService = teamDiscussionService;
        this.teamMemberMapper = teamMemberMapper;
        this.wsPusher = wsPusher;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        wsPusher.register(session); // 等待 AUTH
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Map<String, Object> msg;
        try {
            msg = objectMapper.readValue(message.getPayload(),
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                    });
        } catch (Exception e) {
            wsPusher.sendJson(session, Map.of("type", "ERROR", "message", "消息格式错误"));
            return;
        }
        String type = String.valueOf(msg.get("type"));

        if ("AUTH".equals(type)) {
            handleAuth(session, String.valueOf(msg.get("token")));
            return;
        }

        if (!wsPusher.isAuthenticated(session)) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("未认证"));
            return;
        }
        Long userId = wsPusher.userIdOf(session);

        try {
            switch (type) {
                case "HALL": {
                    String content = String.valueOf(msg.get("content"));
                    ChatMessage saved = chatService.sendHall(userId, content);
                    wsPusher.broadcast(Map.of("type", "HALL", "data", saved));
                    break;
                }
                case "DM": {
                    Object to = msg.get("toUserId");
                    if (to == null) {
                        wsPusher.sendJson(session, Map.of("type", "ERROR", "message", "缺少 toUserId"));
                        return;
                    }
                    Long toUserId = Long.valueOf(String.valueOf(to));
                    String content = String.valueOf(msg.get("content"));
                    ChatMessage saved = chatService.sendDm(userId, toUserId, content);
                    Map<String, Object> out = Map.of("type", "DM", "data", saved);
                    wsPusher.pushToUser(toUserId, out);
                    wsPusher.pushToUser(userId, out); // 发送方其他标签页同步
                    break;
                }
                case "TEAM": {
                    Object teamObj = msg.get("teamId");
                    if (teamObj == null) {
                        wsPusher.sendJson(session, Map.of("type", "ERROR", "message", "缺少 teamId"));
                        return;
                    }
                    Long teamId = Long.valueOf(String.valueOf(teamObj));
                    String content = String.valueOf(msg.get("content"));
                    TeamDiscussion saved = teamDiscussionService.post(teamId, userId, content);
                    broadcastToTeam(teamId, Map.of("type", "TEAM", "data", saved));
                    break;
                }
                default:
                    wsPusher.sendJson(session, Map.of("type", "ERROR", "message", "未知消息类型: " + type));
            }
        } catch (BusinessException e) {
            // 业务拦截（脏话/关注门控/单条限制等）：回传错误消息，保持连接不断开
            wsPusher.sendJson(session, Map.of("type", "ERROR", "message", e.getMessage()));
        }
    }

    private void handleAuth(WebSocketSession session, String token) throws IOException {
        try {
            if (token == null || token.isBlank() || !jwtService.isAccessToken(token)) {
                wsPusher.remove(session);
                session.close(CloseStatus.POLICY_VIOLATION.withReason("无效的令牌"));
                return;
            }
            Long userId = jwtService.getUserId(token);
            wsPusher.authenticate(session, userId);
            wsPusher.sendJson(session, Map.of("type", "AUTH_OK"));
            log.info("WebSocket 认证成功, userId={}", userId);
        } catch (JwtException | IllegalArgumentException e) {
            wsPusher.remove(session);
            session.close(CloseStatus.POLICY_VIOLATION.withReason("令牌无效或已过期"));
        }
    }

    /** 推送给队伍全体在线成员 */
    private void broadcastToTeam(Long teamId, Map<String, Object> payload) {
        java.util.List<TeamMember> members = teamMemberMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getTeamId, teamId));
        for (TeamMember member : members) {
            wsPusher.pushToUser(member.getUserId(), payload);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        wsPusher.remove(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        wsPusher.remove(session);
        try {
            session.close(CloseStatus.SERVER_ERROR);
        } catch (IOException ignored) {
        }
    }
}
