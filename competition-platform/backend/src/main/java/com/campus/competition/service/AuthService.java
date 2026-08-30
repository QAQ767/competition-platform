package com.campus.competition.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.competition.common.BusinessException;
import com.campus.competition.dto.LoginReq;
import com.campus.competition.dto.RegisterReq;
import com.campus.competition.entity.User;
import com.campus.competition.mapper.SkillTagMapper;
import com.campus.competition.mapper.UserMapper;
import com.campus.competition.mapper.UserSkillMapper;
import com.campus.competition.security.JwtService;
import com.campus.competition.security.RedisTokenService;
import com.campus.competition.support.UserVOHelper;
import com.campus.competition.vo.AuthVO;
import com.campus.competition.vo.UserVO;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 认证服务（v2.1）：Spring Security + JWT 双 Token + Redis（限流/黑名单）
 */
@Service
public class AuthService {

    /** 5 分钟内登录失败上限 */
    private static final int MAX_LOGIN_FAILURES = 5;
    private static final long LOGIN_LOCK_SECONDS = 300;
    private static final String LOGIN_RATE_KEY = "rate:login:";

    private final UserMapper userMapper;
    private final UserSkillMapper userSkillMapper;
    private final SkillTagMapper skillTagMapper;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;
    private final RedisTokenService redisTokenService;
    private final OperationLogService operationLogService;

    public AuthService(UserMapper userMapper, UserSkillMapper userSkillMapper,
                       SkillTagMapper skillTagMapper, JwtService jwtService,
                       PasswordEncoder passwordEncoder,
                       StringRedisTemplate redisTemplate, RedisTokenService redisTokenService,
                       OperationLogService operationLogService) {
        this.userMapper = userMapper;
        this.userSkillMapper = userSkillMapper;
        this.skillTagMapper = skillTagMapper;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.redisTemplate = redisTemplate;
        this.redisTokenService = redisTokenService;
        this.operationLogService = operationLogService;
    }

    public AuthVO register(RegisterReq req) {
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, req.getUsername()));
        if (count > 0) {
            throw new BusinessException("用户名已存在");
        }
        User user = new User();
        user.setUsername(req.getUsername());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setNickname(req.getUsername());
        user.setEmail(req.getEmail());
        user.setCollege(req.getCollege());
        user.setMajor(req.getMajor());
        user.setRole("STUDENT");
        // 隐私友好的默认状态：仅浏览，用户可主动开启"希望被邀请"
        user.setCompeteStatus("ONLY_VIEW");
        user.setCreatedAt(LocalDateTime.now());
        userMapper.insert(user);
        operationLogService.record(user.getId(), "用户注册", "register username=" + req.getUsername());
        return buildAuth(user);
    }

    public AuthVO login(LoginReq req) {
        checkLoginLock(req.getUsername());
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, req.getUsername()));
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            recordLoginFailure(req.getUsername());
            throw new BusinessException("用户名或密码错误");
        }
        clearLoginFailures(req.getUsername());
        operationLogService.record(user.getId(), "用户登录", "login username=" + req.getUsername());
        return buildAuth(user);
    }

    /**
     * 刷新令牌：校验 refresh token（含黑名单）后签发新的 access + refresh，
     * 旧 refresh token 立即作废（轮换机制，防重放）
     */
    public AuthVO refresh(String refreshToken) {
        Long userId;
        Claims oldClaims;
        try {
            if (!jwtService.isRefreshToken(refreshToken)) {
                throw new BusinessException(401, "无效的刷新令牌");
            }
            oldClaims = jwtService.parse(refreshToken);
            if (redisTokenService.isBlacklisted(oldClaims.getId())) {
                throw new BusinessException(401, "刷新令牌已失效，请重新登录");
            }
            userId = jwtService.getUserId(refreshToken);
        } catch (JwtException e) {
            throw new BusinessException(401, "刷新令牌已过期，请重新登录");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(401, "用户不存在，请重新登录");
        }
        // 轮换：旧 refresh token 加入黑名单直至其自然过期
        long ttl = (oldClaims.getExpiration().getTime() - System.currentTimeMillis()) / 1000;
        redisTokenService.blacklist(oldClaims.getId(), ttl);
        operationLogService.record(userId, "刷新令牌", "refresh userId=" + userId);
        return buildAuth(user);
    }

    /**
     * 登出：将当前 access token 加入黑名单（Redis），实现主动下线
     */
    public void logout(String accessToken) {
        try {
            Claims claims = jwtService.parse(accessToken);
            if (jwtService.isAccessToken(accessToken)) {
                long ttl = (claims.getExpiration().getTime() - System.currentTimeMillis()) / 1000;
                redisTokenService.blacklist(claims.getId(), ttl);
                operationLogService.record(Long.valueOf(claims.getSubject()), "退出登录", "logout");
            }
        } catch (JwtException | IllegalArgumentException e) {
            // token 本身已无效，无需处理
        }
    }

    public UserVO me(Long userId) {
        User user = userMapper.selectById(userId);
        return UserVOHelper.toUserVO(user, userSkillMapper, skillTagMapper);
    }

    private AuthVO buildAuth(User user) {
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getUsername(), user.getRole());
        String refreshToken = jwtService.generateRefreshToken(user.getId(), user.getUsername(), user.getRole());
        UserVO userVO = UserVOHelper.toUserVO(user, userSkillMapper, skillTagMapper);
        return new AuthVO(accessToken, refreshToken, userVO);
    }

    // ===== 登录防爆破（Redis 计数器） =====

    private void checkLoginLock(String username) {
        String value = redisTemplate.opsForValue().get(LOGIN_RATE_KEY + username);
        if (value != null && Integer.parseInt(value) >= MAX_LOGIN_FAILURES) {
            throw new BusinessException(429, "登录失败次数过多，请 " + (LOGIN_LOCK_SECONDS / 60) + " 分钟后再试");
        }
    }

    private void recordLoginFailure(String username) {
        String key = LOGIN_RATE_KEY + username;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, Duration.ofSeconds(LOGIN_LOCK_SECONDS));
        }
    }

    private void clearLoginFailures(String username) {
        redisTemplate.delete(LOGIN_RATE_KEY + username);
    }
}
