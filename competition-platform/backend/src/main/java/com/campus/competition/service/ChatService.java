package com.campus.competition.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.competition.common.BusinessException;
import com.campus.competition.common.PageVO;
import com.campus.competition.entity.ChatMessage;
import com.campus.competition.entity.User;
import com.campus.competition.mapper.ChatMessageMapper;
import com.campus.competition.mapper.UserMapper;
import com.campus.competition.vo.ConversationVO;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 聊天服务：大厅聊天（HALL）+ 私聊（DM）
 * Demo 采用前端轮询实现实时性；正式版可升级为 WebSocket/SSE。
 */
@Service
public class ChatService {

    private final ChatMessageMapper chatMessageMapper;
    private final UserMapper userMapper;
    private final FollowService followService;
    private final ContentFilterService contentFilterService;

    public ChatService(ChatMessageMapper chatMessageMapper, UserMapper userMapper,
                       FollowService followService, ContentFilterService contentFilterService) {
        this.chatMessageMapper = chatMessageMapper;
        this.userMapper = userMapper;
        this.followService = followService;
        this.contentFilterService = contentFilterService;
    }

    /**
     * 大厅消息分页（第 1 页为最新一页，返回升序便于展示）
     */
    public PageVO<ChatMessage> hallMessages(long page, long size) {
        Page<ChatMessage> result = chatMessageMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getRoomType, "HALL")
                        .orderByDesc(ChatMessage::getId));
        List<ChatMessage> records = new ArrayList<>(result.getRecords());
        java.util.Collections.reverse(records);
        return new PageVO<>(records, result.getTotal(), result.getCurrent(), result.getSize(), result.getPages());
    }

    public ChatMessage sendHall(Long userId, String content) {
        checkClean(content);
        return send(userId, null, "HALL", content);
    }

    /**
     * 私聊（v9.0 关注门控）：
     *  - 回复对方消息（对方已私聊过我）→ 直接可发，无需关注
     *  - 主动发起 → 需先关注对方；对方回复或关注回来之前只能发送 1 条
     */
    public ChatMessage sendDm(Long userId, Long otherId, String content) {
        if (userMapper.selectById(otherId) == null) {
            throw new BusinessException("对方用户不存在");
        }
        boolean otherReplied = chatMessageMapper.selectCount(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getRoomType, "DM")
                .eq(ChatMessage::getUserId, otherId)
                .eq(ChatMessage::getReceiverId, userId)) > 0;
        if (!otherReplied) {
            if (!followService.isFollowing(userId, otherId)) {
                throw new BusinessException("请先关注对方，再发送私聊消息");
            }
            boolean unlocked = followService.isFollowing(otherId, userId);
            if (!unlocked) {
                long sent = chatMessageMapper.selectCount(new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getRoomType, "DM")
                        .eq(ChatMessage::getUserId, userId)
                        .eq(ChatMessage::getReceiverId, otherId));
                if (sent >= 1) {
                    throw new BusinessException("对方尚未回复，暂时只能发送一条私聊消息");
                }
            }
        }
        checkClean(content);
        return send(userId, otherId, "DM", content);
    }

    private void checkClean(String content) {
        if (!contentFilterService.isClean(content)) {
            throw new BusinessException("消息包含不文明用语，已被拦截");
        }
    }

    /**
     * 与指定用户的私聊消息分页（升序返回）
     */
    public PageVO<ChatMessage> dmMessages(Long userId, Long otherId, long page, long size) {
        Page<ChatMessage> result = chatMessageMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getRoomType, "DM")
                        .and(w -> w.eq(ChatMessage::getUserId, userId)
                                .eq(ChatMessage::getReceiverId, otherId)
                                .or()
                                .eq(ChatMessage::getUserId, otherId)
                                .eq(ChatMessage::getReceiverId, userId))
                        .orderByDesc(ChatMessage::getId));
        List<ChatMessage> records = new ArrayList<>(result.getRecords());
        java.util.Collections.reverse(records);
        return new PageVO<>(records, result.getTotal(), result.getCurrent(), result.getSize(), result.getPages());
    }

    /** 我的私聊会话列表（含未读数） */
    public List<ConversationVO> conversations(Long userId) {
        List<ChatMessage> all = chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getRoomType, "DM")
                        .and(w -> w.eq(ChatMessage::getUserId, userId)
                                .or()
                                .eq(ChatMessage::getReceiverId, userId))
                        .orderByAsc(ChatMessage::getId));

        Map<Long, ConversationVO> map = new LinkedHashMap<>();
        for (ChatMessage m : all) {
            Long other = m.getUserId().equals(userId) ? m.getReceiverId() : m.getUserId();
            if (other == null) {
                continue;
            }
            ConversationVO cv = map.computeIfAbsent(other, k -> {
                User u = userMapper.selectById(k);
                return new ConversationVO(k, u == null ? "用户" + k : u.getNickname());
            });
            cv.setLastMessage(m.getContent());
            cv.setLastTime(m.getCreateTime());
            if (m.getReceiverId().equals(userId) && !m.getIsRead()) {
                cv.setUnread(cv.getUnread() + 1);
            }
        }
        List<ConversationVO> result = new ArrayList<>(map.values());
        result.sort((a, b) -> {
            if (a.getLastTime() == null) return 1;
            if (b.getLastTime() == null) return -1;
            return b.getLastTime().compareTo(a.getLastTime());
        });
        return result;
    }

    /** 将对方发给我的私聊标记为已读 */
    public void markDmRead(Long userId, Long otherId) {
        List<ChatMessage> unread = chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getRoomType, "DM")
                        .eq(ChatMessage::getUserId, otherId)
                        .eq(ChatMessage::getReceiverId, userId)
                        .eq(ChatMessage::getIsRead, false));
        for (ChatMessage m : unread) {
            m.setIsRead(true);
            chatMessageMapper.updateById(m);
        }
    }

    private ChatMessage send(Long userId, Long receiverId, String roomType, String content) {
        User user = userMapper.selectById(userId);
        ChatMessage message = new ChatMessage();
        message.setRoomType(roomType);
        message.setUserId(userId);
        message.setUserName(user == null ? "未知" : user.getNickname());
        message.setReceiverId(receiverId);
        message.setContent(content);
        message.setIsRead(receiverId == null);
        message.setCreateTime(LocalDateTime.now());
        chatMessageMapper.insert(message);
        return message;
    }
}
