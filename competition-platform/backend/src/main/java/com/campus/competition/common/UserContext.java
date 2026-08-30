package com.campus.competition.common;

/**
 * 当前登录用户上下文 (ThreadLocal)
 * 由 JwtAuthenticationFilter 在校验通过后写入, 请求结束后清理
 */
public final class UserContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    private UserContext() {
    }

    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    public static Long getUserId() {
        return USER_ID.get();
    }

    public static void clear() {
        USER_ID.remove();
    }
}
