package com.campus.competition.vo;

import java.util.List;

/**
 * 用户主页视图（关注功能）
 */
public class UserHomeVO {

    private UserVO user;
    private boolean isSelf;
    private boolean isFollowing;
    private long followerCount;
    private long followingCount;
    /** 近期已通过的成果/获奖记录 */
    private List<AchievementVO> achievements;

    public UserVO getUser() {
        return user;
    }

    public void setUser(UserVO user) {
        this.user = user;
    }

    public boolean isSelf() {
        return isSelf;
    }

    public void setSelf(boolean self) {
        isSelf = self;
    }

    public boolean isFollowing() {
        return isFollowing;
    }

    public void setFollowing(boolean following) {
        isFollowing = following;
    }

    public long getFollowerCount() {
        return followerCount;
    }

    public void setFollowerCount(long followerCount) {
        this.followerCount = followerCount;
    }

    public long getFollowingCount() {
        return followingCount;
    }

    public void setFollowingCount(long followingCount) {
        this.followingCount = followingCount;
    }

    public List<AchievementVO> getAchievements() {
        return achievements;
    }

    public void setAchievements(List<AchievementVO> achievements) {
        this.achievements = achievements;
    }
}
