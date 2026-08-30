package com.campus.competition.controller;

import com.campus.competition.common.Result;
import com.campus.competition.common.UserContext;
import com.campus.competition.dto.ChatSendReq;
import com.campus.competition.entity.ChatMessage;
import com.campus.competition.service.ChatService;
import com.campus.competition.vo.ConversationVO;
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
 * 聊天接口：大厅聊天 + 私聊
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/hall")
    public Result<com.campus.competition.common.PageVO<ChatMessage>> hallMessages(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "30") long size) {
        return Result.success(chatService.hallMessages(page, size));
    }

    @PostMapping("/hall")
    public Result<ChatMessage> sendHall(@Valid @RequestBody ChatSendReq req) {
        return Result.success(chatService.sendHall(UserContext.getUserId(), req.getContent()));
    }

    @GetMapping("/dm/{userId}")
    public Result<com.campus.competition.common.PageVO<ChatMessage>> dmMessages(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "30") long size) {
        return Result.success(chatService.dmMessages(UserContext.getUserId(), userId, page, size));
    }

    @PostMapping("/dm/{userId}")
    public Result<ChatMessage> sendDm(@PathVariable Long userId, @Valid @RequestBody ChatSendReq req) {
        return Result.success(chatService.sendDm(UserContext.getUserId(), userId, req.getContent()));
    }

    @GetMapping("/dm/conversations")
    public Result<List<ConversationVO>> conversations() {
        return Result.success(chatService.conversations(UserContext.getUserId()));
    }

    @PostMapping("/dm/{userId}/read")
    public Result<Void> markDmRead(@PathVariable Long userId) {
        chatService.markDmRead(UserContext.getUserId(), userId);
        return Result.success();
    }
}
