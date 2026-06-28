package com.gjl.music.auth.security;

import com.gjl.music.config.ConfigService;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.stream.Collectors;

/**
 * JWT Token 工具 —— 签发、验证、解析。
 *
 * <h3>设计要点</h3>
 * <ul>
 *   <li>密钥和过期时间每次从 {@link ConfigService} 热读取，修改配置后立即生效</li>
 *   <li>Access Token 包含 username + authorities</li>
 *   <li>Refresh Token 只包含 username + type 标识</li>
 * </ul>
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private static final String AUTHORITIES_KEY = "auth";
    private static final String TOKEN_TYPE_KEY = "type";
    private static final String VERSION_KEY = "ver";
    private static final String TOKEN_TYPE_ACCESS = "access";
    private static final String TOKEN_TYPE_REFRESH = "refresh";

    private final ConfigService configService;

    public JwtTokenProvider(ConfigService configService) {
        this.configService = configService;
    }

    // ── Token 签发 ──

    public String createAccessToken(Authentication authentication, long version) {
        return createAccessToken(authentication.getName(),
                authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.toSet())
                        .stream().toList(),
                version);
    }

    public String createAccessToken(String username,
                                    java.util.Collection<? extends GrantedAuthority> authorities,
                                    long version) {
        return createAccessToken(username, authorities.stream()
                .map(GrantedAuthority::getAuthority).toList(), version);
    }

    public String createAccessToken(String username, java.util.List<String> authorities) {
        return createAccessToken(username, authorities, 0);
    }

    public String createAccessToken(String username, java.util.List<String> authorities, long version) {
        long expirationMs = getAccessTokenExpirationMs();
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        var builder = Jwts.builder()
                .subject(username)
                .claim(AUTHORITIES_KEY, String.join(",", authorities))
                .claim(TOKEN_TYPE_KEY, TOKEN_TYPE_ACCESS)
                .issuedAt(now)
                .expiration(expiry);

        if (version > 0) {
            builder.claim(VERSION_KEY, version);
        }

        return builder.signWith(getSecretKey()).compact();
    }

    public String createRefreshToken(UserDetails userDetails) {
        long expirationMs = getRefreshTokenExpirationMs();
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(userDetails.getUsername())
                .claim(TOKEN_TYPE_KEY, TOKEN_TYPE_REFRESH)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSecretKey())
                .compact();
    }

    // ── Token 验证 ──

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    public boolean isAccessToken(String token) {
        try {
            Claims claims = parseClaims(token);
            return TOKEN_TYPE_ACCESS.equals(claims.get(TOKEN_TYPE_KEY));
        } catch (JwtException e) {
            return false;
        }
    }

    // ── Token 解析 ──

    public String getUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public Authentication getAuthentication(String token) {
        Claims claims = parseClaims(token);
        String authorities = claims.get(AUTHORITIES_KEY, String.class);
        var auths = authorities != null && !authorities.isEmpty()
                ? java.util.Arrays.stream(authorities.split(","))
                    .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new)
                    .toList()
                : java.util.List.<GrantedAuthority>of();
        return new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                claims.getSubject(), token, auths);
    }

    /** 从 token 中提取版本号（权限变更后用于检测旧 token） */
    public long getVersion(String token) {
        try {
            Claims claims = parseClaims(token);
            Object v = claims.get(VERSION_KEY);
            if (v instanceof Number n) return n.longValue();
            return 0;
        } catch (JwtException e) {
            return 0;
        }
    }

    // ── 内部方法 ──

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSecretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSecretKey() {
        String secret = configService.getString("music.auth.jwt.secret", "");
        if (secret.isBlank()) {
            throw new IllegalStateException("JWT secret is not configured");
        }
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        // 如果密钥长度不足，使用 SHA-256 派生 256-bit 密钥
        if (keyBytes.length < 32) {
            try {
                keyBytes = java.security.MessageDigest.getInstance("SHA-256").digest(keyBytes);
            } catch (java.security.NoSuchAlgorithmException e) {
                throw new IllegalStateException("SHA-256 not available", e);
            }
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private long getAccessTokenExpirationMs() {
        return configService.getInt("music.auth.jwt.access-token-expiration-seconds", 1800) * 1000L;
    }

    private long getRefreshTokenExpirationMs() {
        return configService.getInt("music.auth.jwt.refresh-token-expiration-seconds", 604800) * 1000L;
    }
}
