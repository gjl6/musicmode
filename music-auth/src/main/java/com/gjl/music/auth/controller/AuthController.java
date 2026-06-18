package com.gjl.music.auth.controller;

import com.gjl.music.auth.dto.LoginRequest;
import com.gjl.music.auth.dto.PermissionResponse;
import com.gjl.music.auth.dto.RefreshRequest;
import com.gjl.music.auth.dto.RegisterRequest;
import com.gjl.music.auth.dto.TokenResponse;
import com.gjl.music.auth.mapper.AuthRoleMapper;
import com.gjl.music.auth.mapper.AuthUserMapper;
import com.gjl.music.auth.model.AuthRole;
import com.gjl.music.auth.model.AuthUser;
import com.gjl.music.auth.security.JwtTokenProvider;
import com.gjl.music.auth.security.AuthCacheService;
import com.gjl.music.auth.service.AuthService;
import com.gjl.music.auth.service.PermissionAdminService;
import com.gjl.music.config.ConfigService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


@Slf4j
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String JWT_COOKIE_NAME = "music-jwt";

    private static final int DEFAULT_ACCESS_EXPIRATION_SEC = 1800;

    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthUserMapper authUserMapper;
    private final AuthRoleMapper authRoleMapper;
    private final PermissionAdminService permissionAdminService;
    private final AuthCacheService authCache;
    private final ConfigService configService;

    public AuthController(AuthService authService,
                          JwtTokenProvider jwtTokenProvider,
                          AuthUserMapper authUserMapper,
                          AuthRoleMapper authRoleMapper,
                          PermissionAdminService permissionAdminService,
                          AuthCacheService authCache,
                          ConfigService configService) {
        this.authService = authService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.authUserMapper = authUserMapper;
        this.authRoleMapper = authRoleMapper;
        this.permissionAdminService = permissionAdminService;
        this.authCache = authCache;
        this.configService = configService;
    }


    private static Map<String, Object> errorBody(int code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        body.put("data", null);
        return body;
    }


    private void setJwtCookie(HttpServletResponse response, String token) {
        int maxAge = configService.getInt(
                "music.auth.jwt.access-token-expiration-seconds", DEFAULT_ACCESS_EXPIRATION_SEC);
        Cookie cookie = new Cookie(JWT_COOKIE_NAME, token);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(maxAge);
        cookie.setAttribute("SameSite", "Lax");
                response.addCookie(cookie);
        log.debug("JWT Cookie 已设置: maxAge={}s", maxAge);
    }


    private void clearJwtCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(JWT_COOKIE_NAME, "");
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(0);
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
        log.debug("JWT Cookie 已清除");
    }


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
                .roles(roles)
                .build());
    }


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

                List<String> cachedCodes = authCache.getPermissions(user.getId());
        if (cachedCodes != null) {
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

                var perms = permissionAdminService.getUserPermissionInfos(user.getId());

                List<String> codes = perms.stream().map(PermissionResponse::getPermissionCode).toList();
        authCache.putPermissions(user.getId(), codes);

        return ResponseEntity.ok(perms);
    }

    private String resolveToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
}
