package com.campus.competition.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 服务：签发与校验 Access Token / Refresh Token
 *
 * 结构：subject=userId，claims 含 username、role、type（ACCESS/REFRESH）
 */
@Component
public class JwtService {

    private static final String CLAIM_TYPE = "type";
    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_ROLE = "role";
    private static final String TYPE_ACCESS = "ACCESS";
    private static final String TYPE_REFRESH = "REFRESH";

    private final SecretKey key;
    private final long accessExpireMillis;
    private final long refreshExpireMillis;

    public JwtService(@Value("${jwt.secret}") String secret,
                      @Value("${jwt.access-expire-seconds:7200}") long accessExpireSeconds,
                      @Value("${jwt.refresh-expire-seconds:604800}") long refreshExpireSeconds) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessExpireMillis = accessExpireSeconds * 1000;
        this.refreshExpireMillis = refreshExpireSeconds * 1000;
    }

    public String generateAccessToken(Long userId, String username, String role) {
        return generate(userId, username, role, TYPE_ACCESS, accessExpireMillis);
    }

    public String generateRefreshToken(Long userId, String username, String role) {
        return generate(userId, username, role, TYPE_REFRESH, refreshExpireMillis);
    }

    private String generate(Long userId, String username, String role, String type, long expireMillis) {
        Date now = new Date();
        return Jwts.builder()
                .setId(java.util.UUID.randomUUID().toString().replace("-", ""))
                .setSubject(String.valueOf(userId))
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_ROLE, role)
                .claim(CLAIM_TYPE, type)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + expireMillis))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 解析并校验签名与有效期；非法/过期会抛出 JwtException
     */
    public Claims parse(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public Long getUserId(String token) {
        return Long.valueOf(parse(token).getSubject());
    }

    public boolean isAccessToken(String token) {
        return TYPE_ACCESS.equals(parse(token).get(CLAIM_TYPE, String.class));
    }

    public boolean isRefreshToken(String token) {
        return TYPE_REFRESH.equals(parse(token).get(CLAIM_TYPE, String.class));
    }
}
