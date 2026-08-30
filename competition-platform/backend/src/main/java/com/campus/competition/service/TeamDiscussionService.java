package com.campus.competition.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.competition.common.BusinessException;
import com.campus.competition.common.PageVO;
import com.campus.competition.entity.Team;
import com.campus.competition.entity.TeamDiscussion;
import com.campus.competition.entity.User;
import com.campus.competition.mapper.TeamDiscussionMapper;
import com.campus.competition.mapper.TeamMapper;
import com.campus.competition.mapper.UserMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 团队讨论区服务（仅队伍成员可查看/发言）
 */
@Service
public class TeamDiscussionService {

    private final TeamDiscussionMapper teamDiscussionMapper;
    private final TeamMapper teamMapper;
    private final UserMapper userMapper;
    private final ResourceService resourceService;
    private final ContentFilterService contentFilterService;

    public TeamDiscussionService(TeamDiscussionMapper teamDiscussionMapper, TeamMapper teamMapper,
                                 UserMapper userMapper, ResourceService resourceService,
                                 ContentFilterService contentFilterService) {
        this.teamDiscussionMapper = teamDiscussionMapper;
        this.teamMapper = teamMapper;
        this.userMapper = userMapper;
        this.resourceService = resourceService;
        this.contentFilterService = contentFilterService;
    }

    public PageVO<TeamDiscussion> list(Long teamId, Long userId, long page, long size) {
        requireMember(teamId, userId);
        Page<TeamDiscussion> result = teamDiscussionMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<TeamDiscussion>()
                        .eq(TeamDiscussion::getTeamId, teamId)
                        .orderByDesc(TeamDiscussion::getId));
        java.util.Collections.reverse(result.getRecords()); // 升序展示
        return PageVO.from(result);
    }

    public TeamDiscussion post(Long teamId, Long userId, String content) {
        requireMember(teamId, userId);
        if (!contentFilterService.isClean(content)) {
            throw new BusinessException("消息包含不文明用语，已被拦截");
        }
        User user = userMapper.selectById(userId);
        TeamDiscussion discussion = new TeamDiscussion();
        discussion.setTeamId(teamId);
        discussion.setUserId(userId);
        discussion.setUserName(user == null ? "未知" : user.getNickname());
        discussion.setContent(content);
        discussion.setCreateTime(LocalDateTime.now());
        teamDiscussionMapper.insert(discussion);
        return discussion;
    }

    public void delete(Long id, Long userId) {
        TeamDiscussion discussion = teamDiscussionMapper.selectById(id);
        if (discussion == null) {
            throw new BusinessException("消息不存在");
        }
        Team team = teamMapper.selectById(discussion.getTeamId());
        boolean isAuthor = discussion.getUserId().equals(userId);
        boolean isCaptain = team != null && team.getCaptainId().equals(userId);
        if (!isAuthor && !isCaptain) {
            throw new BusinessException("仅发布者或队长可删除");
        }
        teamDiscussionMapper.deleteById(id);
    }

    private void requireMember(Long teamId, Long userId) {
        if (teamMapper.selectById(teamId) == null) {
            throw new BusinessException("队伍不存在");
        }
        if (!resourceService.isMember(teamId, userId)) {
            throw new BusinessException("仅队伍成员可查看讨论区");
        }
    }
}
