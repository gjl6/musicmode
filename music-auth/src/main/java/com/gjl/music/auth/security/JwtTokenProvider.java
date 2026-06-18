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
