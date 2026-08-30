package com.campus.competition.security;

import com.campus.competition.common.UserContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;

/**
 * JWT 认证过滤器：
 * 解析 Authorization: Bearer <token>，
 * 校验通过后写入 SecurityContext（供 Spring Security 鉴权）与 UserContext（供业务代码取当前用户）。
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final RedisTokenService redisTokenService;

    public JwtAuthenticationFilter(JwtService jwtService, RedisTokenService redisTokenService) {
        this.jwtService = jwtService;
        this.redisTokenService = redisTokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Claims claims = jwtService.parse(token);
                if (jwtService.isAccessToken(token) && !redisTokenService.isBlacklisted(claims.getId())) {
                    Long userId = Long.valueOf(claims.getSubject());
                    String role = claims.get("role", String.class);

                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            userId, null,
                            Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role)));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    UserContext.setUserId(userId);
                }
            } catch (JwtException | IllegalArgumentException e) {
                // token 非法/过期：不设置认证信息，由 Spring Security 的 EntryPoint 返回 401
                SecurityContextHolder.clearContext();
            }
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            UserContext.clear();
        }
    }
}
