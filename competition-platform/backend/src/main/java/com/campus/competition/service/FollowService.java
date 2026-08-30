package com.campus.competition.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.competition.common.BusinessException;
import com.campus.competition.entity.Follow;
import com.campus.competition.entity.User;
import com.campus.competition.mapper.FollowMapper;
import com.campus.competition.mapper.UserMapper;
import com.campus.competition.vo.UserVO;
import com.campus.competition.support.UserVOHelper;
import com.campus.competition.mapper.SkillTagMapper;
import com.campus.competition.mapper.UserSkillMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 关注服务：关注/取关/查询（用户主页与私聊门控使用）
 */
@Service
public class FollowService {

    private final FollowMapper followMapper;
    private final UserMapper userMapper;
    private final UserSkillMapper userSkillMapper;
    private final SkillTagMapper skillTagMapper;

    public FollowService(FollowMapper followMapper, UserMapper userMapper,
                         UserSkillMapper userSkillMapper, SkillTagMapper skillTagMapper) {
        this.followMapper = followMapper;
        this.userMapper = userMapper;
        this.userSkillMapper = userSkillMapper;
        this.skillTagMapper = skillTagMapper;
    }

    public boolean isFollowing(Long followerId, Long followeeId) {
        return followMapper.selectCount(
                new LambdaQueryWrapper<Follow>()
                        .eq(Follow::getFollowerId, followerId)
                        .eq(Follow::getFolloweeId, followeeId)) > 0;
    }

    /** 关注（重复关注忽略） */
    public void follow(Long followerId, Long followeeId) {
        if (followerId.equals(followeeId)) {
            throw new BusinessException("不能关注自己");
        }
        if (userMapper.selectById(followeeId) == null) {
            throw new BusinessException("用户不存在");
        }
        if (!isFollowing(followerId, followeeId)) {
            Follow follow = new Follow();
            follow.setFollowerId(followerId);
            follow.setFolloweeId(followeeId);
            follow.setCreateTime(LocalDateTime.now());
            followMapper.insert(follow);
        }
    }

    public void unfollow(Long followerId, Long followeeId) {
        followMapper.delete(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getFollowerId, followerId)
                .eq(Follow::getFolloweeId, followeeId));
    }

    public long followerCount(Long userId) {
        return followMapper.selectCount(
                new LambdaQueryWrapper<Follow>().eq(Follow::getFolloweeId, userId));
    }

    public long followingCount(Long userId) {
        return followMapper.selectCount(
                new LambdaQueryWrapper<Follow>().eq(Follow::getFollowerId, userId));
    }

    /** 我的关注列表 */
    public List<UserVO> followingUsers(Long userId) {
        return followMapper.selectList(new LambdaQueryWrapper<Follow>()
                        .eq(Follow::getFollowerId, userId)
                        .orderByDesc(Follow::getId))
                .stream().map(f -> userMapper.selectById(f.getFolloweeId()))
                .filter(u -> u != null)
                .map(u -> UserVOHelper.toUserVO(u, userSkillMapper, skillTagMapper))
                .collect(Collectors.toList());
    }

    /** 我的粉丝列表 */
    public List<UserVO> followerUsers(Long userId) {
        return followMapper.selectList(new LambdaQueryWrapper<Follow>()
                        .eq(Follow::getFolloweeId, userId)
                        .orderByDesc(Follow::getId))
                .stream().map(f -> userMapper.selectById(f.getFollowerId()))
                .filter(u -> u != null)
                .map(u -> UserVOHelper.toUserVO(u, userSkillMapper, skillTagMapper))
                .collect(Collectors.toList());
    }
}
