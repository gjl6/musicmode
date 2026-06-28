package com.gjl.music.auth.security;

import com.gjl.music.config.ConfigService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * JWT 认证过滤器 —— 从 Authorization header 提取 Token 并设置 SecurityContext。
 */
@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String JWT_COOKIE_NAME = "music-jwt";
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final JwtTokenProvider jwtTokenProvider;
    private final ConfigService configService;
    private final AuthCacheService authCache;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider,
                                   ConfigService configService,
                                   AuthCacheService authCache) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.configService = configService;
        this.authCache = authCache;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String token = resolveToken(request);
        String tokenSource = "Header";
        if (!StringUtils.hasText(token)) {
            token = request.getParameter("token");
            tokenSource = "QueryParam";
        }
        if (!StringUtils.hasText(token)) {
            token = resolveCookieToken(request);
            tokenSource = "Cookie";
        }
        if (StringUtils.hasText(token)) {
            log.info("[JWT Filter] 从{}解析到 token: path={}, token前8位={}",
                    tokenSource, request.getServletPath(),
                    token.substring(0, Math.min(8, token.length())));
        } else {
            log.info("[JWT Filter] 未找到 token: path={}, method={}",
                    request.getServletPath(), request.getMethod());
        }
        if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
            // 检查 token 版本号：如果权限已变更，旧 token 立即失效
            String username = jwtTokenProvider.getUsername(token);
            long tokenVer = jwtTokenProvider.getVersion(token);
            long currentVer = authCache.getVersion(username);
            if (currentVer > 0 && tokenVer < currentVer) {
                log.debug("Token 版本过期: username={}, tokenVer={}, currentVer={}",
                        username, tokenVer, currentVer);
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":401,\"message\":\"权限已变更，请重新登录\"}");
                return;
            }

            Authentication auth = jwtTokenProvider.getAuthentication(token);
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        // 白名单路径不拦截
        List<String> whiteList = configService.getList("music.auth.white-list-paths", List.of());
        for (String pattern : whiteList) {
            if (PATH_MATCHER.match(pattern.trim(), path)) {
                return true;
            }
        }
        // 固定白名单
        return PATH_MATCHER.match("/ws/**", path)
                || PATH_MATCHER.match("/h2-console/**", path)
                || PATH_MATCHER.match("/actuator/**", path)
                || PATH_MATCHER.match("/error", path);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    /** 从 Cookie 中提取 JWT（供 img/audio 等无法自定义 Header 的标签使用）。 */
    private String resolveCookieToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        return Arrays.stream(cookies)
                .filter(c -> JWT_COOKIE_NAME.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
