package com.campus.competition.controller;

import com.campus.competition.common.BusinessException;
import com.campus.competition.common.Result;
import com.campus.competition.common.UserContext;
import com.campus.competition.service.AchievementService;
import com.campus.competition.service.FollowService;
import com.campus.competition.support.UserVOHelper;
import com.campus.competition.vo.UserHomeVO;
import com.campus.competition.mapper.UserMapper;
import com.campus.competition.mapper.UserSkillMapper;
import com.campus.competition.mapper.SkillTagMapper;
import com.campus.competition.entity.User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import com.campus.competition.vo.UserVO;

/**
 * 用户主页与关注接口
 */
@RestController
@RequestMapping("/api/users")
public class UserProfileController {

    private final UserMapper userMapper;
    private final UserSkillMapper userSkillMapper;
    private final SkillTagMapper skillTagMapper;
    private final FollowService followService;
    private final AchievementService achievementService;

    public UserProfileController(UserMapper userMapper, UserSkillMapper userSkillMapper,
                                 SkillTagMapper skillTagMapper, FollowService followService,
                                 AchievementService achievementService) {
        this.userMapper = userMapper;
        this.userSkillMapper = userSkillMapper;
        this.skillTagMapper = skillTagMapper;
        this.followService = followService;
        this.achievementService = achievementService;
    }

    /** 用户主页：资料 + 关注状态 + 近期获奖记录 */
    @GetMapping("/{id}")
    public Result<UserHomeVO> home(@PathVariable Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        Long viewerId = UserContext.getUserId();
        UserHomeVO vo = new UserHomeVO();
        vo.setUser(UserVOHelper.toUserVO(user, userSkillMapper, skillTagMapper));
        vo.setSelf(viewerId != null && viewerId.equals(id));
        vo.setFollowing(viewerId != null && followService.isFollowing(viewerId, id));
        vo.setFollowerCount(followService.followerCount(id));
        vo.setFollowingCount(followService.followingCount(id));
        vo.setAchievements(achievementService.listApprovedByUser(id));
        return Result.success(vo);
    }

    /** 关注用户（需登录） */
    @PostMapping("/{id}/follow")
    public Result<Void> follow(@PathVariable Long id) {
        followService.follow(UserContext.getUserId(), id);
        return Result.success();
    }

    /** 取消关注 */
    @DeleteMapping("/{id}/follow")
    public Result<Void> unfollow(@PathVariable Long id) {
        followService.unfollow(UserContext.getUserId(), id);
        return Result.success();
    }

    /** 我关注的用户列表 */
    @GetMapping("/me/following")
    public Result<List<UserVO>> following() {
        return Result.success(followService.followingUsers(UserContext.getUserId()));
    }

    /** 关注我的用户列表 */
    @GetMapping("/me/followers")
    public Result<List<UserVO>> followers() {
        return Result.success(followService.followerUsers(UserContext.getUserId()));
    }
}
