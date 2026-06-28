package com.gjl.music.auth.service;

import com.gjl.music.auth.dto.LoginRequest;
import com.gjl.music.auth.dto.MenuNode;
import com.gjl.music.auth.dto.RegisterRequest;
import com.gjl.music.auth.dto.TokenResponse;
import com.gjl.music.auth.mapper.AuthPermissionMapper;
import com.gjl.music.auth.mapper.AuthRefreshTokenMapper;
import com.gjl.music.auth.mapper.AuthRoleMapper;
import com.gjl.music.auth.mapper.AuthUserMapper;
import com.gjl.music.auth.model.AuthPermission;
import com.gjl.music.auth.model.AuthRefreshToken;
import com.gjl.music.auth.model.AuthRole;
import com.gjl.music.auth.model.AuthUser;
import com.gjl.music.auth.security.AuthCacheService;
import com.gjl.music.auth.security.JwtTokenProvider;
import com.gjl.music.auth.security.SubsonicSecretCrypto;
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

/**
 * 认证业务逻辑。
 */
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
    private final MenuService menuService;
    private final SubsonicSecretCrypto subsonicSecretCrypto;

    public AuthService(AuthUserMapper authUserMapper,
                       AuthRoleMapper authRoleMapper,
                       AuthPermissionMapper authPermissionMapper,
                       AuthRefreshTokenMapper refreshTokenMapper,
                       JwtTokenProvider jwtTokenProvider,
                       PasswordEncoder passwordEncoder,
                       UserDetailsService userDetailsService,
                       ConfigService configService,
                       AuthCacheService authCache,
                       MenuService menuService,
                       SubsonicSecretCrypto subsonicSecretCrypto) {
        this.authUserMapper = authUserMapper;
        this.authRoleMapper = authRoleMapper;
        this.authPermissionMapper = authPermissionMapper;
        this.refreshTokenMapper = refreshTokenMapper;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordEncoder = passwordEncoder;
        this.userDetailsService = userDetailsService;
        this.configService = configService;
        this.authCache = authCache;
        this.menuService = menuService;
        this.subsonicSecretCrypto = subsonicSecretCrypto;
    }

    // ── 登录 ──

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

        // 更新登录时间
        authUserMapper.updateLoginTime(user.getId());

        // 如果 subsonic_secret 未初始化 → 用当前明文密码加密存储（升级已有用户）
        if (user.getSubsonicSecret() == null || user.getSubsonicSecret().isBlank()) {
            String encrypted = subsonicSecretCrypto.encrypt(request.password());
            authUserMapper.updateSubsonicSecret(user.getId(), encrypted);
            log.info("用户 {} 的 subsonic_secret 已初始化（通过登录升级）", user.getUsername());
        }

        return issueTokens(user);
    }

    // ── 注册 ──

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
        user.setSubsonicSecret(subsonicSecretCrypto.encrypt(request.password()));
        user.setEmail(request.email());
        user.setDisplayName(
                request.displayName() != null ? request.displayName() : request.username());
        user.setStatus(1);

        authUserMapper.insert(user);

        // 新用户默认赋予 USER 角色
        AuthRole userRole = authRoleMapper.selectByCode("ROLE_USER");
        if (userRole != null) {
            authRoleMapper.insertUserRole(user.getId(), userRole.getId());
        }

        return issueTokens(user);
    }

    // ── Token 刷新 ──

    @Transactional
    public TokenResponse refreshToken(String refreshToken) {
        // 1. 验证 refreshToken（Redis → DB）
        String tokenHash = hashToken(refreshToken);
        var cached = authCache.getRefreshToken(tokenHash);

        if (cached != null && cached.revoked()) {
            throw new BadCredentialsException("Refresh token 已失效");
        }
        if (cached != null && cached.expiresAt() < System.currentTimeMillis() / 1000) {
            throw new BadCredentialsException("Refresh token 已过期");
        }

        // Redis 未命中 → 查 DB
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

        // 2. 吊销旧 token（DB + Redis）
        refreshTokenMapper.revokeByToken(refreshToken);
        authCache.revokeRefreshToken(tokenHash);

        // 3. 签发新 token 对
        AuthUser user = authUserMapper.selectById(userId);
        if (user == null || user.getStatus() != 1) {
            throw new BadCredentialsException("用户不存在或已被禁用");
        }

        return issueTokens(user);
    }

    // ── 登出 ──

    @Transactional
    public void logout(Long userId) {
        refreshTokenMapper.revokeByUserId(userId);
        // Redis 中的 refreshToken 以 hash 为 key 无法逐条清理，
        // 依赖 accessToken 版本号 + refreshToken TTL 自然过期
    }

    // ── 修改密码（自服务：需验证旧密码）──

    @Transactional
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        AuthUser user = authUserMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BadCredentialsException("当前密码错误");
        }
        if (oldPassword.equals(newPassword)) {
            throw new IllegalArgumentException("新密码不能与当前密码相同");
        }

        authUserMapper.updatePassword(userId, passwordEncoder.encode(newPassword),
                subsonicSecretCrypto.encrypt(newPassword));
        // 密码变更 → 吊销所有 refreshToken + 失效缓存 → 强制重新登录
        refreshTokenMapper.revokeByUserId(userId);
        authCache.incrementVersion(user.getUsername());
        authCache.evictUser(user.getUsername());

        log.info("用户 {} 修改了密码", user.getUsername());
    }

    // ── 更新个人资料 ──

    @Transactional
    public AuthUser updateProfile(Long userId, String displayName, String email) {
        AuthUser user = authUserMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }

        if (displayName != null && !displayName.isBlank()) {
            user.setDisplayName(displayName.trim());
        }
        if (email != null) {
            user.setEmail(email.trim());
        }
        authUserMapper.updateById(user);

        // 资料变更 → 失效缓存
        authCache.evictUser(user.getUsername());

        log.info("用户 {} 更新了个人资料", user.getUsername());
        return user;
    }

    // ── 内部方法 ──

    /** 对 refreshToken 做哈希后用做缓存 key（避免明文存储完整 token） */
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

    // ── 签发 Token ──

    private TokenResponse issueTokens(AuthUser user) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername());

        // 获取当前 token 版本号（权限变更时递增，确保旧 token 能被检测到）
        long version = authCache.getVersion(user.getUsername());
        String accessToken = jwtTokenProvider.createAccessToken(
                user.getUsername(), userDetails.getAuthorities(), version);
        String refreshToken = jwtTokenProvider.createRefreshToken(userDetails);

        // 先吊销该用户所有旧 refreshToken，再插入新 token（唯一约束：每用户一个活跃 token）
        refreshTokenMapper.revokeByUserId(user.getId());

        // 存储 refreshToken（用于吊销和并发控制）
        long refreshExpirationSec = configService.getInt(
                "music.auth.jwt.refresh-token-expiration-seconds", 604800);
        LocalDateTime expiresAt = LocalDateTime.ofInstant(
                Instant.now().plusSeconds(refreshExpirationSec), ZoneId.systemDefault());

        AuthRefreshToken tokenEntity = new AuthRefreshToken(
                user.getId(), refreshToken, expiresAt);
        refreshTokenMapper.insert(tokenEntity);

        // 缓存 refreshToken 到 Redis（加速后续校验）
        String tokenHash = hashToken(refreshToken);
        authCache.putRefreshToken(tokenHash,
                new AuthCacheService.CachedRefreshToken(
                        user.getId(),
                        expiresAt.atZone(ZoneId.systemDefault()).toEpochSecond(),
                        false));

        // 构建响应
        List<String> roles = authRoleMapper.selectByUserId(user.getId()).stream()
                .map(AuthRole::getRoleCode)
                .toList();

        boolean isAdmin = roles.contains("ROLE_ADMIN");
        List<String> permissions = authPermissionMapper.selectByUserId(user.getId()).stream()
                .map(AuthPermission::getPermissionCode)
                .toList();

        // 按权限构建菜单树
        List<MenuNode> menus = menuService.buildMenuTree(permissions, isAdmin);

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
                        .avatarPath(user.getAvatarPath())
                        .roles(roles)
                        .build())
                .menus(menus)
                .build();
    }

    // ════════════════ 初始化默认管理员 ════════════════

    @PostConstruct
    @Transactional
    public void initDefaultAdmin() {
        String defaultUsername = configService.getString(
                "music.auth.default-admin-username", "admin");
        String defaultPassword = configService.getString(
                "music.auth.default-admin-password", "admin123");

        // 确保角色存在（DDL 已预置，此处兜底）
        ensureRole("ROLE_ADMIN", "管理员", "系统管理员，拥有所有权限");
        ensureRole("ROLE_USER", "普通用户", "普通用户，基本的音乐管理权限");

        // 如果默认管理员不存在，创建
        if (!authUserMapper.existsByUsername(defaultUsername)) {
            AuthUser admin = new AuthUser();
            admin.setUsername(defaultUsername);
            admin.setPassword(passwordEncoder.encode(defaultPassword));
            admin.setSubsonicSecret(subsonicSecretCrypto.encrypt(defaultPassword));
            admin.setEmail("admin@localhost");
            admin.setDisplayName("系统管理员");
            admin.setStatus(1);

            authUserMapper.insert(admin);

            // 赋予 ADMIN 角色
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
