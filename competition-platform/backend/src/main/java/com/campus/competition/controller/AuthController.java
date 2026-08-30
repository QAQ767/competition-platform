package com.campus.competition.controller;

import com.campus.competition.common.Result;
import com.campus.competition.common.UserContext;
import com.campus.competition.dto.LoginReq;
import com.campus.competition.dto.RefreshReq;
import com.campus.competition.dto.RegisterReq;
import com.campus.competition.service.AuthService;
import com.campus.competition.vo.AuthVO;
import com.campus.competition.vo.UserVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

/**
 * 认证接口（v2.0）：登录/注册/刷新令牌/当前用户
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public Result<AuthVO> register(@Valid @RequestBody RegisterReq req) {
        return Result.success(authService.register(req));
    }

    @PostMapping("/login")
    public Result<AuthVO> login(@Valid @RequestBody LoginReq req) {
        return Result.success(authService.login(req));
    }

    @PostMapping("/refresh")
    public Result<AuthVO> refresh(@Valid @RequestBody RefreshReq req) {
        return Result.success(authService.refresh(req.getRefreshToken()));
    }

    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            authService.logout(header.substring(7));
        }
        return Result.success();
    }

    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.success(authService.me(UserContext.getUserId()));
    }
}
