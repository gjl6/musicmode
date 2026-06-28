package com.gjl.music.auth.config;

import com.gjl.music.auth.AuthInitializedMarker;
import com.gjl.music.auth.exception.AuthModuleInitException;
import com.gjl.music.auth.mapper.AuthUserMapper;
import com.gjl.music.auth.model.AuthUser;
import com.gjl.music.auth.security.JwtAccessDeniedHandler;
import com.gjl.music.auth.security.JwtAuthenticationEntryPoint;
import com.gjl.music.auth.security.JwtAuthenticationFilter;
import com.gjl.music.auth.security.SubsonicSecretCrypto;
import com.gjl.music.config.ConfigService;
import com.gjl.music.playback.infra.security.SubsonicAuthFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security 核心配置。
 *
 * <h3>启动控制</h3>
 * {@link #authInitializedMarker} Bean 验证 JWT secret 存在，
 * 失败则整个 Spring context 无法加载。
 */
@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    /** 固定白名单路径（无需认证即可访问，所有 HTTP 方法） */
    private static final String[] ALWAYS_PERMIT = {
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/refresh",
            "/ws/**",
            "/h2-console/**",
            "/actuator/health",
            "/error",
            "/rest/**",   // Subsonic API（自有认证过滤器）
            "/",
            "/index.html",
            "/favicon.ico",
            "/assets/**", // 前端静态资源
    };

    /** 仅 GET 公开的路径（PUT/POST 等写操作需认证） */
    private static final String[] ALWAYS_PERMIT_GET = {
            "/api/auth/register-status",
    };

    // ── SecurityFilterChain ──

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtAuthenticationFilter jwtFilter,
                                           JwtAuthenticationEntryPoint entryPoint,
                                           JwtAccessDeniedHandler deniedHandler,
                                           AuthUserMapper authUserMapper,
                                           PasswordEncoder passwordEncoder,
                                           SubsonicSecretCrypto subsonicSecretCrypto) throws Exception {

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // ★ 第一优先级：放行所有 OPTIONS 预检请求
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // 仅 GET 公开（PUT/POST 需认证）
                        .requestMatchers(HttpMethod.GET, ALWAYS_PERMIT_GET).permitAll()
                        // 白名单路径（全部方法放行）
                        .requestMatchers(ALWAYS_PERMIT).permitAll()
                        // RBAC（后续 Phase B 细化）
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // 其余全部需要认证
                        .anyRequest().authenticated()
                )
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler)
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(subsonicAuthFilter(authUserMapper, passwordEncoder, subsonicSecretCrypto),
                        JwtAuthenticationFilter.class);

        return http.build();
    }

    // ── SubsonicSecretCrypto（AES 加密/解密，用于 token+salt 认证）──

    @Bean
    public SubsonicSecretCrypto subsonicSecretCrypto(ConfigService configService, Environment env) {
        // 优先从 DB 读取，若 ConfigInitializer 尚未同步则回退到 application.yml
        String jwtSecret = configService.getString("music.auth.jwt.secret", null);
        if (jwtSecret == null || jwtSecret.isBlank()) {
            jwtSecret = env.getProperty("music.auth.jwt.secret");
        }
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new AuthModuleInitException(
                    "music.auth.jwt.secret is required for SubsonicSecretCrypto");
        }
        return new SubsonicSecretCrypto(jwtSecret);
    }

    // ── Subsonic 认证过滤器（BCrypt + Token+Salt 双重验证）──

    @Bean
    public SubsonicAuthFilter subsonicAuthFilter(AuthUserMapper authUserMapper,
                                                  PasswordEncoder passwordEncoder,
                                                  SubsonicSecretCrypto subsonicSecretCrypto) {
        SubsonicAuthFilter.PasswordLookup lookup = (username, password) -> {
            AuthUser user = authUserMapper.selectByUsername(username);
            if (user == null || user.getPassword() == null) {
                return false;
            }
            return passwordEncoder.matches(password, user.getPassword());
        };

        SubsonicAuthFilter.SubsonicTokenVerifier tokenVerifier = username -> {
            AuthUser user = authUserMapper.selectByUsername(username);
            if (user == null || user.getSubsonicSecret() == null) {
                return null;
            }
            return subsonicSecretCrypto.decrypt(user.getSubsonicSecret());
        };

        return new SubsonicAuthFilter(lookup, tokenVerifier);
    }

    // ── 全局 CORS ──

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    // ── 密码编码器 ──

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    // ── 标记 Bean：auth 模块初始化完成 ──

    @Bean
    @ConditionalOnBean(ConfigService.class)
    public AuthInitializedMarker authInitializedMarker(ConfigService configService) {
        String secret = configService.getString("music.auth.jwt.secret", null);
        if (secret == null || secret.isBlank()) {
            log.error("AUTH MODULE INIT FAILED: music.auth.jwt.secret is not configured. "
                    + "Application will NOT start.");
            throw new AuthModuleInitException(
                    "music.auth.jwt.secret is not configured. Application will NOT start.");
        }
        log.info("Auth module initialized successfully — JWT secret verified");
        return new AuthInitializedMarker() {};
    }
}
