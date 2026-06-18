package com.gjl.music.auth.config;

import com.gjl.music.auth.AuthInitializedMarker;
import com.gjl.music.auth.exception.AuthModuleInitException;
import com.gjl.music.auth.security.JwtAccessDeniedHandler;
import com.gjl.music.auth.security.JwtAuthenticationEntryPoint;
import com.gjl.music.auth.security.JwtAuthenticationFilter;
import com.gjl.music.config.ConfigService;
import com.gjl.music.playback.infra.security.SubsonicAuthFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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


@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {


    private static final String[] ALWAYS_PERMIT = {
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/refresh",
            "/ws/**",
            "/h2-console/**",
            "/actuator/health",
            "/error",
            "/rest/**",
    };


    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtAuthenticationFilter jwtFilter,
                                           JwtAuthenticationEntryPoint entryPoint,
                                           JwtAccessDeniedHandler deniedHandler) throws Exception {

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                                                .requestMatchers(ALWAYS_PERMIT).permitAll()
                                                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                                                .anyRequest().authenticated()
                )
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler)
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new SubsonicAuthFilter(null), JwtAuthenticationFilter.class);

        return http.build();
    }


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


    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }


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
