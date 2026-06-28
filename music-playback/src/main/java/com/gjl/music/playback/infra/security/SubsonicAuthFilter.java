package com.gjl.music.playback.infra.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Subsonic API 认证过滤器。
 *
 * <h3>认证方式（按优先级）</h3>
 * <ol>
 *   <li><b>密码认证</b>（{@code u} + {@code p}）— BCrypt 验证</li>
 *   <li><b>Token+Salt 认证</b>（{@code u} + {@code t} + {@code s}）—
 *       优先从会话缓存验证；缓存未命中则通过 {@link SubsonicTokenVerifier}
 *       解密存储的明文密码计算 MD5 验证</li>
 * </ol>
 *
 * <h3>BCrypt 兼容性</h3>
 * <p>BCrypt 单向哈希无法计算 {@code MD5(password + salt)}。
 * 解决方案：用户登录/注册时 AES 加密明文密码存入 {@code subsonic_secret} 列，
 * Subsonic 认证时解密后计算 MD5。
 */
@Slf4j
public class SubsonicAuthFilter extends OncePerRequestFilter {

    /** 会话缓存 TTL（分钟）— 超时后客户端需重新发送密码 */
    private static final int SESSION_TTL_MINUTES = 30;

    /** 密码验证接口（由 music-auth 注入 BCrypt 实现） */
    @FunctionalInterface
    public interface PasswordLookup {
        boolean verifyPassword(String username, String password);
    }

    /**
     * Token+Salt 验证接口。
     * 返回明文密码用于计算 MD5(password + salt)，返回 null 表示不适用。
     */
    @FunctionalInterface
    public interface SubsonicTokenVerifier {
        /** @return 明文密码，或 null（用户无 subsonic_secret） */
        @Nullable
        String getPlaintextPassword(String username);
    }

    private final PasswordLookup passwordLookup;
    private final SubsonicTokenVerifier tokenVerifier;

    /**
     * 会话缓存：username → 密码认证成功后缓存。
     * TTL 30 分钟，过期后 token+salt 请求走 SubsonicTokenVerifier。
     */
    private final Cache<String, Boolean> sessionCache = Caffeine.newBuilder()
            .expireAfterWrite(SESSION_TTL_MINUTES, TimeUnit.MINUTES)
            .maximumSize(1000)
            .build();

    public SubsonicAuthFilter(PasswordLookup passwordLookup,
                              SubsonicTokenVerifier tokenVerifier) {
        this.passwordLookup = passwordLookup;
        this.tokenVerifier = tokenVerifier;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();

        if (!path.startsWith("/rest/")) {
            chain.doFilter(request, response);
            return;
        }

        if (SecurityContextHolder.getContext().getAuthentication() != null &&
                SecurityContextHolder.getContext().getAuthentication().isAuthenticated()) {
            log.debug("[SubsonicAuth] 已有 JWT 认证，跳过: path={}", path);
            chain.doFilter(request, response);
            return;
        }

        // ── 公开端点（无认证参数时放行）──
        String simplePath = path.replace(".view", "");
        boolean isPublicEndpoint = simplePath.endsWith("/rest/ping")
                || simplePath.endsWith("/rest/getLicense")
                || simplePath.endsWith("/rest/getOpenSubsonicExtensions");
        boolean hasAuthParams = request.getParameter("u") != null
                && (request.getParameter("p") != null
                    || request.getParameter("t") != null);
        if (isPublicEndpoint && !hasAuthParams) {
            log.debug("[SubsonicAuth] 公开端点（无认证参数），跳过认证: {}", simplePath);
            chain.doFilter(request, response);
            return;
        }

        if (authenticate(request)) {
            chain.doFilter(request, response);
            return;
        }

        String u = request.getParameter("u");
        String p = request.getParameter("p");
        String t = request.getParameter("t");
        String s = request.getParameter("s");
        log.warn("[SubsonicAuth] 认证失败，返回 Subsonic error 40: path={}, hasU={}, hasP={}, hasT={}, hasS={}",
                path, u != null, p != null, t != null, s != null);
        writeSubsonicError(response, request.getParameter("f"), 40, "用户名或密码错误");
    }

    private boolean authenticate(HttpServletRequest request) {
        if (passwordLookup == null) {
            log.warn("[SubsonicAuth] PasswordLookup 未注入！");
            return false;
        }

        String username = request.getParameter("u");
        if (username == null || username.isBlank()) return false;

        String pass = request.getParameter("p");
        String token = request.getParameter("t");
        String salt = request.getParameter("s");
        String version = request.getParameter("v");
        String client = request.getParameter("c");
        String format = request.getParameter("f");

        // ★ 打印收到的所有 Subsonic 参数（含原始 query string）
        String qs = request.getQueryString();
        log.info("[SubsonicAuth] 收到请求: path={}, queryString={}", request.getRequestURI(), qs);
        log.info("[SubsonicAuth] 解析参数: u={}, v={}, c={}, f={}, hasP={}, hasT={}, hasS={}, t_len={}, s_len={}",
                username, version, client, format,
                pass != null, token != null, salt != null,
                token != null ? token.length() : 0, salt != null ? salt.length() : 0);
        if (token != null && token.length() > 0) {
            log.info("[SubsonicAuth] token 前8位={}, salt 全文={}", token.substring(0, Math.min(8, token.length())), salt);
        }

        // 1) 密码认证（p 参数）
        if (pass != null && !pass.isBlank()) {
            if (pass.startsWith("enc:")) {
                try {
                    byte[] decoded = HexFormat.of().parseHex(pass.substring(4));
                    pass = new String(decoded);
                } catch (IllegalArgumentException e) {
                    log.debug("[SubsonicAuth] hex 解码失败，按原文使用");
                }
            }

            if (passwordLookup.verifyPassword(username, pass)) {
                sessionCache.put(username, Boolean.TRUE);
                log.info("[SubsonicAuth] password 认证成功: username={}", username);
                setAuth(username);
                return true;
            }
            log.warn("[SubsonicAuth] password 认证失败: username={}", username);
            return false;
        }

        // 2) Token+Salt 认证
        if (token != null && !token.isBlank() && salt != null && !salt.isBlank()) {
            // 2a) 先检查会话缓存
            if (Boolean.TRUE.equals(sessionCache.getIfPresent(username))) {
                log.debug("[SubsonicAuth] 会话缓存命中: username={}", username);
                setAuth(username);
                return true;
            }

            // 2b) 通过 SubsonicTokenVerifier 获取明文密码计算 MD5
            if (tokenVerifier != null) {
                String plaintext = tokenVerifier.getPlaintextPassword(username);
                if (plaintext != null && !plaintext.isBlank()) {
                    String expected = md5Hex(plaintext + salt);
                    log.info("[SubsonicAuth] token+salt 验证: username={}, plaintext_len={}, salt={}, computed_md5前8位={}, received_token前8位={}",
                            username, plaintext.length(), salt,
                            expected.substring(0, Math.min(8, expected.length())),
                            token.substring(0, Math.min(8, token.length())));
                    if (expected.equalsIgnoreCase(token)) {
                        sessionCache.put(username, Boolean.TRUE);
                        log.info("[SubsonicAuth] token+salt 认证成功: username={}", username);
                        setAuth(username);
                        return true;
                    }
                    log.warn("[SubsonicAuth] token+salt MD5 不匹配: username={}", username);
                } else {
                    log.warn("[SubsonicAuth] subsonic_secret 未初始化，无法验证 token+salt: username={}", username);
                }
            }
            log.warn("[SubsonicAuth] token+salt 认证失败（缓存+验证器均未命中）: username={}", username);
            return false;
        }

        return false;
    }

    private void setAuth(String username) {
        var auth = new UsernamePasswordAuthenticationToken(
                username, null,
                List.of(new SimpleGrantedAuthority("music:play"),
                        new SimpleGrantedAuthority("music:read")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // ══════════════════════════════════════════════════
    // MD5
    // ══════════════════════════════════════════════════

    private static String md5Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 not available", e);
        }
    }

    // ══════════════════════════════════════════════════
    // Subsonic 标准错误响应
    // ══════════════════════════════════════════════════

    private static void writeSubsonicError(HttpServletResponse response, String format,
                                           int code, String message) throws IOException {
        if ("json".equals(format) || "jsonp".equals(format)) {
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(String.format(
                    "{\"subsonic-response\":{"
                    + "\"status\":\"failed\","
                    + "\"version\":\"1.16.1\","
                    + "\"type\":\"Music Mode\","
                    + "\"openSubsonic\":true,"
                    + "\"error\":{\"code\":%d,\"message\":\"%s\"}"
                    + "}}", code, escapeJson(message)));
        } else {
            response.setContentType("application/xml;charset=UTF-8");
            response.getWriter().write(String.format(
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<subsonic-response xmlns=\"http://subsonic.org/restapi\""
                    + " status=\"failed\" version=\"1.16.1\""
                    + " type=\"Music Mode\" openSubsonic=\"true\">"
                    + "<error code=\"%d\" message=\"%s\"/>"
                    + "</subsonic-response>", code, escapeXmlAttr(message)));
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String escapeXmlAttr(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
