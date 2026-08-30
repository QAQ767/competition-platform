package com.campus.competition.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.competition.common.BusinessException;
import com.campus.competition.common.PageVO;
import com.campus.competition.dto.FeedbackReq;
import com.campus.competition.entity.Feedback;
import com.campus.competition.entity.User;
import com.campus.competition.mapper.FeedbackMapper;
import com.campus.competition.mapper.UserMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 赛事/功能反馈服务：学生提交平台没有的赛事需求，管理员处理
 */
@Service
public class FeedbackService {

    private final FeedbackMapper feedbackMapper;
    private final UserMapper userMapper;

    public FeedbackService(FeedbackMapper feedbackMapper, UserMapper userMapper) {
        this.feedbackMapper = feedbackMapper;
        this.userMapper = userMapper;
    }

    public Feedback submit(Long userId, FeedbackReq req) {
        User user = userMapper.selectById(userId);
        Feedback feedback = new Feedback();
        feedback.setUserId(userId);
        feedback.setUserName(user == null ? "未知" : user.getNickname());
        feedback.setType(req.getType());
        feedback.setContent(req.getContent());
        feedback.setStatus("待处理");
        feedback.setCreateTime(LocalDateTime.now());
        feedbackMapper.insert(feedback);
        return feedback;
    }

    public PageVO<Feedback> myFeedback(Long userId, long page, long size) {
        Page<Feedback> result = feedbackMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Feedback>()
                        .eq(Feedback::getUserId, userId)
                        .orderByDesc(Feedback::getId));
        return PageVO.from(result);
    }

    public PageVO<Feedback> listAll(Long operatorId, long page, long size) {
        checkAdmin(operatorId);
        Page<Feedback> result = feedbackMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Feedback>().orderByDesc(Feedback::getId));
        return PageVO.from(result);
    }

    /** 未处理的反馈数量（管理员红点提醒用） */
    public long pendingCount() {
        return feedbackMapper.selectCount(
                new LambdaQueryWrapper<Feedback>().eq(Feedback::getStatus, "待处理"));
    }

    public void handle(Long id, Long operatorId) {
        checkAdmin(operatorId);
        Feedback feedback = feedbackMapper.selectById(id);
        if (feedback == null) {
            throw new BusinessException("反馈不存在");
        }
        feedback.setStatus("已处理");
        feedbackMapper.updateById(feedback);
    }

    private void checkAdmin(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || !"ADMIN".equals(user.getRole())) {
            throw new BusinessException("需要管理员权限");
        }
    }
}
