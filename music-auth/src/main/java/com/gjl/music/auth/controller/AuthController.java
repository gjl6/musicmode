package com.gjl.music.auth.controller;

import com.gjl.music.auth.dto.ChangePasswordRequest;
import com.gjl.music.auth.dto.LoginRequest;
import com.gjl.music.auth.dto.PermissionResponse;
import com.gjl.music.auth.dto.RefreshRequest;
import com.gjl.music.auth.dto.RegisterRequest;
import com.gjl.music.auth.dto.TokenResponse;
import com.gjl.music.auth.dto.UpdateProfileRequest;
import com.gjl.music.auth.mapper.AuthRoleMapper;
import com.gjl.music.auth.mapper.AuthUserMapper;
import com.gjl.music.auth.model.AuthRole;
import com.gjl.music.auth.model.AuthUser;
import com.gjl.music.auth.security.JwtTokenProvider;
import com.gjl.music.auth.security.AuthCacheService;
import com.gjl.music.auth.service.AuthService;
import com.gjl.music.auth.service.MenuService;
import com.gjl.music.auth.service.PermissionAdminService;
import com.gjl.music.config.ConfigService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map;

/**
 * 认证 REST API。
 *
 * <p>登录/注册/刷新时同时设置 {@code music-jwt} HttpOnly Cookie，
 * 供 img/audio 等无法自定义请求头的标签自动携带 JWT。
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String JWT_COOKIE_NAME = "music-jwt";
    /** 默认 token 过期时间（秒） */
    private static final int DEFAULT_ACCESS_EXPIRATION_SEC = 1800;

    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthUserMapper authUserMapper;
    private final AuthRoleMapper authRoleMapper;
    private final PermissionAdminService permissionAdminService;
    private final AuthCacheService authCache;
    private final ConfigService configService;
    private final MenuService menuService;

    public AuthController(AuthService authService,
                          JwtTokenProvider jwtTokenProvider,
                          AuthUserMapper authUserMapper,
                          AuthRoleMapper authRoleMapper,
                          PermissionAdminService permissionAdminService,
                          AuthCacheService authCache,
                          ConfigService configService,
                          MenuService menuService) {
        this.authService = authService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.authUserMapper = authUserMapper;
        this.authRoleMapper = authRoleMapper;
        this.permissionAdminService = permissionAdminService;
        this.authCache = authCache;
        this.configService = configService;
        this.menuService = menuService;
    }

    // ── 统一错误响应（避免 Map.of() 不接受 null 值）──

    private static Map<String, Object> errorBody(int code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        body.put("data", null);
        return body;
    }

    // ── Cookie 工具 ──

    /** 设置 JWT HttpOnly Cookie（供 img/audio 等标签自动携带） */
    private void setJwtCookie(HttpServletResponse response, String token) {
        int maxAge = configService.getInt(
                "music.auth.jwt.access-token-expiration-seconds", DEFAULT_ACCESS_EXPIRATION_SEC);
        Cookie cookie = new Cookie(JWT_COOKIE_NAME, token);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(maxAge);
        cookie.setAttribute("SameSite", "Lax");
        // 不设置 Domain — 浏览器自动绑定到当前源，适配开发/生产环境
        response.addCookie(cookie);
        log.debug("JWT Cookie 已设置: maxAge={}s", maxAge);
    }

    /** 清除 JWT Cookie */
    private void clearJwtCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(JWT_COOKIE_NAME, "");
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(0);
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
        log.debug("JWT Cookie 已清除");
    }

    // ── API ──

    /** 查询注册开关状态（公开，无需登录） */
    @GetMapping("/register-status")
    public ResponseEntity<?> registerStatus() {
        boolean allowed = configService.getBoolean("music.auth.allow-registration", true);
        return ResponseEntity.ok(Map.of("allowRegistration", allowed));
    }

    /** 修改注册开关（需认证 + config:write 权限） */
    @PutMapping("/register-status")
    @PreAuthorize("hasAuthority('config:write')")
    public ResponseEntity<?> updateRegisterStatus(@RequestBody Map<String, Boolean> body) {
        Boolean allowed = body.get("allowRegistration");
        if (allowed == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "缺少 allowRegistration 字段"));
        }
        configService.updateValue("music.auth.allow-registration", String.valueOf(allowed));
        return ResponseEntity.ok(Map.of("success", true, "allowRegistration", allowed));
    }

    /** 登录 */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request,
                                   HttpServletResponse response) {
        try {
            TokenResponse token = authService.login(request);
            setJwtCookie(response, token.getAccessToken());
            return ResponseEntity.ok(token);
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(401).body(errorBody(401, e.getMessage()));
        }
    }

    /** 注册 */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request,
                                      HttpServletResponse response) {
        try {
            TokenResponse token = authService.register(request);
            setJwtCookie(response, token.getAccessToken());
            return ResponseEntity.ok(token);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(errorBody(400, e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(errorBody(403, e.getMessage()));
        }
    }

    /** 刷新 Token */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody RefreshRequest request,
                                     HttpServletResponse response) {
        try {
            TokenResponse token = authService.refreshToken(request.refreshToken());
            setJwtCookie(response, token.getAccessToken());
            return ResponseEntity.ok(token);
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(401).body(errorBody(401, e.getMessage()));
        }
    }

    /** 登出 */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader("Authorization") String authHeader,
                                    HttpServletResponse response) {
        String token = resolveToken(authHeader);
        if (token != null && jwtTokenProvider.validateToken(token)) {
            String username = jwtTokenProvider.getUsername(token);
            AuthUser user = authUserMapper.selectByUsername(username);
            if (user != null) {
                authService.logout(user.getId());
            }
        }
        clearJwtCookie(response);
        return ResponseEntity.ok(errorBody(200, "登出成功"));
    }

    /** 当前用户信息 */
    @GetMapping("/me")
    public ResponseEntity<?> me(@RequestHeader("Authorization") String authHeader) {
        String token = resolveToken(authHeader);
        if (token == null || !jwtTokenProvider.validateToken(token)) {
            return ResponseEntity.status(401).body(errorBody(401, "未认证或 Token 已过期"));
        }

        String username = jwtTokenProvider.getUsername(token);
        AuthUser user = authUserMapper.selectByUsername(username);
        if (user == null) {
            return ResponseEntity.status(401).body(errorBody(401, "用户不存在"));
        }

        // 角色优先 Redis → DB
        List<String> roles = authCache.getRoles(user.getId());
        if (roles == null) {
            roles = authRoleMapper.selectByUserId(user.getId()).stream()
                    .map(AuthRole::getRoleCode)
                    .toList();
            authCache.putRoles(user.getId(), roles);
        }

        return ResponseEntity.ok(TokenResponse.UserInfo.builder()
                .id(user.getId())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .email(user.getEmail())
                .avatarPath(user.getAvatarPath())
                .roles(roles)
                .build());
    }

    /** 当前用户权限列表（含 code + name，Redis 缓存 → DB） */
    @GetMapping("/permissions")
    public ResponseEntity<?> permissions(@RequestHeader("Authorization") String authHeader) {
        String token = resolveToken(authHeader);
        if (token == null || !jwtTokenProvider.validateToken(token)) {
            return ResponseEntity.status(401).body(errorBody(401, "未认证或 Token 已过期"));
        }

        String username = jwtTokenProvider.getUsername(token);
        AuthUser user = authUserMapper.selectByUsername(username);
        if (user == null) {
            return ResponseEntity.status(401).body(errorBody(401, "用户不存在"));
        }

        // 权限优先 Redis → DB（含名称元数据）
        List<String> cachedCodes = authCache.getPermissions(user.getId());
        if (cachedCodes != null) {
            // 需要补元数据名称 —— 从全局权限元数据缓存取
            var allMeta = authCache.getAllPermissions();
            if (allMeta != null) {
                var result = allMeta.stream()
                        .filter(m -> cachedCodes.contains(m.permissionCode()))
                        .map(m -> PermissionResponse.builder()
                                .permissionCode(m.permissionCode())
                                .permissionName(m.permissionName())
                                .description(m.description())
                                .build())
                        .toList();
                return ResponseEntity.ok(result);
            }
        }

        // 缓存未命中 → DB 查询
        var perms = permissionAdminService.getUserPermissionInfos(user.getId());

        // 回填缓存
        List<String> codes = perms.stream().map(PermissionResponse::getPermissionCode).toList();
        authCache.putPermissions(user.getId(), codes);

        return ResponseEntity.ok(perms);
    }

    /** 当前用户菜单/路由树（按权限过滤，供前端动态注册路由） */
    @GetMapping("/menus")
    public ResponseEntity<?> menus(@RequestHeader("Authorization") String authHeader) {
        String token = resolveToken(authHeader);
        if (token == null || !jwtTokenProvider.validateToken(token)) {
            return ResponseEntity.status(401).body(errorBody(401, "未认证或 Token 已过期"));
        }

        String username = jwtTokenProvider.getUsername(token);
        AuthUser user = authUserMapper.selectByUsername(username);
        if (user == null) {
            return ResponseEntity.status(401).body(errorBody(401, "用户不存在"));
        }

        // 角色优先 Redis → DB
        List<String> roles = authCache.getRoles(user.getId());
        if (roles == null) {
            roles = authRoleMapper.selectByUserId(user.getId()).stream()
                    .map(AuthRole::getRoleCode)
                    .toList();
            authCache.putRoles(user.getId(), roles);
        }
        boolean isAdmin = roles.contains("ROLE_ADMIN");

        // 权限码优先 Redis → DB
        List<String> permCodes = authCache.getPermissions(user.getId());
        if (permCodes == null) {
            permCodes = permissionAdminService.getUserPermissionInfos(user.getId()).stream()
                    .map(PermissionResponse::getPermissionCode)
                    .toList();
            authCache.putPermissions(user.getId(), permCodes);
        }

        return ResponseEntity.ok(menuService.buildMenuTree(permCodes, isAdmin));
    }

    /** 修改密码（自服务：需验证旧密码） */
    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestHeader("Authorization") String authHeader,
                                            @Valid @RequestBody ChangePasswordRequest request) {
        String token = resolveToken(authHeader);
        if (token == null || !jwtTokenProvider.validateToken(token)) {
            return ResponseEntity.status(401).body(errorBody(401, "未认证或 Token 已过期"));
        }

        String username = jwtTokenProvider.getUsername(token);
        AuthUser user = authUserMapper.selectByUsername(username);
        if (user == null) {
            return ResponseEntity.status(401).body(errorBody(401, "用户不存在"));
        }

        try {
            authService.changePassword(user.getId(), request.getOldPassword(), request.getNewPassword());
            return ResponseEntity.ok(errorBody(200, "密码修改成功，请重新登录"));
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(400).body(errorBody(400, e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(errorBody(400, e.getMessage()));
        }
    }

    /** 更新个人资料（自服务） */
    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@RequestHeader("Authorization") String authHeader,
                                           @RequestBody UpdateProfileRequest request) {
        String token = resolveToken(authHeader);
        if (token == null || !jwtTokenProvider.validateToken(token)) {
            return ResponseEntity.status(401).body(errorBody(401, "未认证或 Token 已过期"));
        }

        String username = jwtTokenProvider.getUsername(token);
        AuthUser user = authUserMapper.selectByUsername(username);
        if (user == null) {
            return ResponseEntity.status(401).body(errorBody(401, "用户不存在"));
        }

        try {
            AuthUser updated = authService.updateProfile(user.getId(),
                    request.displayName(), request.email());
            return ResponseEntity.ok(TokenResponse.UserInfo.builder()
                    .id(updated.getId())
                    .username(updated.getUsername())
                    .displayName(updated.getDisplayName())
                    .email(updated.getEmail())
                    .avatarPath(updated.getAvatarPath())
                    .build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(errorBody(400, e.getMessage()));
        }
    }

    /** 上传头像 */
    @PostMapping("/avatar")
    public ResponseEntity<?> uploadAvatar(@RequestHeader("Authorization") String authHeader,
                                          @RequestParam("file") MultipartFile file) {
        String token = resolveToken(authHeader);
        if (token == null || !jwtTokenProvider.validateToken(token)) {
            return ResponseEntity.status(401).body(errorBody(401, "未认证或 Token 已过期"));
        }

        String username = jwtTokenProvider.getUsername(token);
        AuthUser user = authUserMapper.selectByUsername(username);
        if (user == null) {
            return ResponseEntity.status(401).body(errorBody(401, "用户不存在"));
        }

        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(errorBody(400, "文件为空"));
            }
            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                return ResponseEntity.badRequest().body(errorBody(400, "仅支持图片格式"));
            }

            // 存储到 {music.base-path}/avatars/{userId}.{ext}
            String basePath = configService.getString("music.base-path", ".");
            Path avatarDir = Paths.get(basePath, "avatars");
            Files.createDirectories(avatarDir);

            // 删除旧头像
            if (user.getAvatarPath() != null) {
                try {
                    Files.deleteIfExists(Paths.get(user.getAvatarPath()));
                } catch (IOException ignored) {}
            }

            // 保存新头像
            String ext = resolveExt(contentType, file.getOriginalFilename());
            String filename = user.getId() + ext;
            Path dest = avatarDir.resolve(filename);
            Files.copy(file.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);

            String avatarPath = dest.toAbsolutePath().toString();
            authUserMapper.updateAvatar(user.getId(), avatarPath);
            user.setAvatarPath(avatarPath);
            // 失效缓存
            authCache.evictUser(user.getUsername());

            log.info("用户 {} 上传了头像: {}", user.getUsername(), avatarPath);
            return ResponseEntity.ok(Map.of("avatarPath", avatarPath));
        } catch (IOException e) {
            log.error("头像上传失败: userId={}", user.getId(), e);
            return ResponseEntity.internalServerError().body(errorBody(500, "头像上传失败"));
        }
    }

    /** 获取用户头像（公开，用于侧边栏等展示） */
    @GetMapping("/avatar/{userId}")
    public ResponseEntity<Resource> getUserAvatar(@PathVariable Long userId) {
        AuthUser user = authUserMapper.selectById(userId);
        if (user == null || user.getAvatarPath() == null) {
            return ResponseEntity.notFound().build();
        }

        Path filePath = Paths.get(user.getAvatarPath());
        if (!Files.exists(filePath) || !Files.isReadable(filePath)) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(filePath);
        String contentType = resolveContentType(filePath);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource);
    }

    private String resolveExt(String contentType, String filename) {
        if (filename != null) {
            int dot = filename.lastIndexOf('.');
            if (dot > 0) return filename.substring(dot).toLowerCase();
        }
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            default -> ".png";
        };
    }

    private String resolveContentType(Path filePath) {
        String name = filePath.getFileName().toString().toLowerCase();
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".gif")) return "image/gif";
        if (name.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }

    private String resolveToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
}
