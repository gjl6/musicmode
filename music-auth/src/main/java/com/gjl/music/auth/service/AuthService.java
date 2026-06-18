package com.gjl.music.auth.service;

import com.gjl.music.auth.dto.LoginRequest;
import com.gjl.music.auth.dto.RegisterRequest;
import com.gjl.music.auth.dto.TokenResponse;
import com.gjl.music.auth.mapper.AuthPermissionMapper;
import com.gjl.music.auth.mapper.AuthRefreshTokenMapper;
import com.gjl.music.auth.mapper.AuthRoleMapper;
import com.gjl.music.auth.mapper.AuthUserMapper;
import com.gjl.music.auth.model.AuthRefreshToken;
import com.gjl.music.auth.model.AuthRole;
import com.gjl.music.auth.model.AuthUser;
import com.gjl.music.auth.security.AuthCacheService;
import com.gjl.music.auth.security.JwtTokenProvider;
import com.gjl.music.config.ConfigService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;


@Slf4j
@Service
public class AuthService {

    private final AuthUserMapper authUserMapper;
    private final AuthRoleMapper authRoleMapper;
    private final AuthPermissionMapper authPermissionMapper;
    private final AuthRefreshTokenMapper refreshTokenMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final UserDetailsService userDetailsService;
    private final ConfigService configService;
    private final AuthCacheService authCache;

    public AuthService(AuthUserMapper authUserMapper,
                       AuthRoleMapper authRoleMapper,
                       AuthPermissionMapper authPermissionMapper,
                       AuthRefreshTokenMapper refreshTokenMapper,
                       JwtTokenProvider jwtTokenProvider,
                       PasswordEncoder passwordEncoder,
                       UserDetailsService userDetailsService,
                       ConfigService configService,
                       AuthCacheService authCache) {
        this.authUserMapper = authUserMapper;
        this.authRoleMapper = authRoleMapper;
        this.authPermissionMapper = authPermissionMapper;
        this.refreshTokenMapper = refreshTokenMapper;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordEncoder = passwordEncoder;
        this.userDetailsService = userDetailsService;
        this.configService = configService;
        this.authCache = authCache;
    }


    @Transactional
    public TokenResponse login(LoginRequest request) {
        AuthUser user = authUserMapper.selectByUsername(request.username());
        if (user == null) {
            throw new BadCredentialsException("用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BadCredentialsException("用户已被禁用");
        }
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BadCredentialsException("用户名或密码错误");
        }

                authUserMapper.updateLoginTime(user.getId());

        return issueTokens(user);
    }


    @Transactional
    public TokenResponse register(RegisterRequest request) {
        if (!configService.getBoolean("music.auth.allow-registration", true)) {
            throw new IllegalStateException("注册功能已关闭");
        }
        if (authUserMapper.existsByUsername(request.username())) {
            throw new IllegalArgumentException("用户名已存在");
        }

        AuthUser user = new AuthUser();
        user.setUsername(request.username());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setEmail(request.email());
        user.setDisplayName(
                request.displayName() != null ? request.displayName() : request.username());
        user.setStatus(1);

        authUserMapper.insert(user);

                AuthRole userRole = authRoleMapper.selectByCode("ROLE_USER");
        if (userRole != null) {
            authRoleMapper.insertUserRole(user.getId(), userRole.getId());
        }

        return issueTokens(user);
    }


    @Transactional
    public TokenResponse refreshToken(String refreshToken) {
                String tokenHash = hashToken(refreshToken);
        var cached = authCache.getRefreshToken(tokenHash);

        if (cached != null && cached.revoked()) {
            throw new BadCredentialsException("Refresh token 已失效");
        }
        if (cached != null && cached.expiresAt() < System.currentTimeMillis() / 1000) {
            throw new BadCredentialsException("Refresh token 已过期");
        }

                Long userId;
        if (cached != null) {
            userId = cached.userId();
        } else {
            AuthRefreshToken stored = refreshTokenMapper.findByToken(refreshToken);
            if (stored == null || Boolean.TRUE.equals(stored.getRevoked())) {
                throw new BadCredentialsException("Refresh token 无效或已被吊销");
            }
            if (stored.getExpiresAt().isBefore(LocalDateTime.now())) {
                throw new BadCredentialsException("Refresh token 已过期");
            }
            userId = stored.getUserId();
        }

        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new BadCredentialsException("Refresh token 验证失败");
        }

                refreshTokenMapper.revokeByToken(refreshToken);
        authCache.revokeRefreshToken(tokenHash);

                AuthUser user = authUserMapper.selectById(userId);
        if (user == null || user.getStatus() != 1) {
            throw new BadCredentialsException("用户不存在或已被禁用");
        }

        return issueTokens(user);
    }


    @Transactional
    public void logout(Long userId) {
        refreshTokenMapper.revokeByUserId(userId);
                    }


    private String hashToken(String token) {
        try {
            var md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            return Integer.toHexString(token.hashCode());
        }
    }


    private TokenResponse issueTokens(AuthUser user) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername());

                long version = authCache.getVersion(user.getUsername());
        String accessToken = jwtTokenProvider.createAccessToken(
                user.getUsername(), userDetails.getAuthorities(), version);
        String refreshToken = jwtTokenProvider.createRefreshToken(userDetails);

                refreshTokenMapper.revokeByUserId(user.getId());

                long refreshExpirationSec = configService.getInt(
                "music.auth.jwt.refresh-token-expiration-seconds", 604800);
        LocalDateTime expiresAt = LocalDateTime.ofInstant(
                Instant.now().plusSeconds(refreshExpirationSec), ZoneId.systemDefault());

        AuthRefreshToken tokenEntity = new AuthRefreshToken(
                user.getId(), refreshToken, expiresAt);
        refreshTokenMapper.insert(tokenEntity);

                String tokenHash = hashToken(refreshToken);
        authCache.putRefreshToken(tokenHash,
                new AuthCacheService.CachedRefreshToken(
                        user.getId(),
                        expiresAt.atZone(ZoneId.systemDefault()).toEpochSecond(),
                        false));

                List<String> roles = authRoleMapper.selectByUserId(user.getId()).stream()
                .map(AuthRole::getRoleCode)
                .toList();

        long accessExpirationSec = configService.getInt(
                "music.auth.jwt.access-token-expiration-seconds", 1800);

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(accessExpirationSec)
                .userInfo(TokenResponse.UserInfo.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .displayName(user.getDisplayName())
                        .email(user.getEmail())
                        .roles(roles)
                        .build())
                .build();
    }


    @PostConstruct
    @Transactional
    public void initDefaultAdmin() {
        String defaultUsername = configService.getString(
                "music.auth.default-admin-username", "admin");
        String defaultPassword = configService.getString(
                "music.auth.default-admin-password", "admin123");

                ensureRole("ROLE_ADMIN", "管理员", "系统管理员，拥有所有权限");
        ensureRole("ROLE_USER", "普通用户", "普通用户，基本的音乐管理权限");

                if (!authUserMapper.existsByUsername(defaultUsername)) {
            AuthUser admin = new AuthUser();
            admin.setUsername(defaultUsername);
            admin.setPassword(passwordEncoder.encode(defaultPassword));
            admin.setEmail("admin@localhost");
            admin.setDisplayName("系统管理员");
            admin.setStatus(1);

            authUserMapper.insert(admin);

                        AuthRole adminRole = authRoleMapper.selectByCode("ROLE_ADMIN");
            if (adminRole != null) {
                authRoleMapper.insertUserRole(admin.getId(), adminRole.getId());
            }

            log.info("Default admin user '{}' created", defaultUsername);
        }
    }

    private void ensureRole(String code, String name, String desc) {
        AuthRole role = authRoleMapper.selectByCode(code);
        if (role == null) {
            role = new AuthRole();
            role.setRoleName(name);
            role.setRoleCode(code);
            role.setDescription(desc);
            authRoleMapper.insert(role);
            log.info("Auth role '{}' created", code);
        }
    }
}
