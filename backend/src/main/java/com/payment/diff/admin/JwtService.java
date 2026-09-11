package com.payment.diff.admin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * JWT 签发与解析（HS256）。无状态会话：subject=账号名，claim 携带角色。
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final Duration defaultTtl;

    public JwtService(@Value("${diffpay.jwt.secret}") String secret,
                      @Value("${diffpay.jwt.expire-minutes}") long expireMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.defaultTtl = Duration.ofMinutes(expireMinutes);
    }

    /** 签发默认有效期的令牌 */
    public String issue(String username, String role) {
        return issue(username, role, defaultTtl);
    }

    /** 签发指定有效期的令牌（ttl 为负可构造已过期令牌，供测试） */
    public String issue(String username, String role, Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    /**
     * 解析并校验签名与有效期。
     * @return 账号名
     * @throws ExpiredJwtException 令牌过期
     * @throws JwtException 签名无效/格式非法
     */
    public String parseUsername(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
        return claims.getSubject();
    }
}
