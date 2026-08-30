package com.campus.competition.vo;

/**
 * 登录/注册/刷新令牌返回视图（v2.0：双 Token）
 */
public class AuthVO {

    private String accessToken;
    private String refreshToken;
    private UserVO user;

    public AuthVO() {
    }

    public AuthVO(String accessToken, String refreshToken, UserVO user) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.user = user;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public UserVO getUser() {
        return user;
    }

    public void setUser(UserVO user) {
        this.user = user;
    }
}
