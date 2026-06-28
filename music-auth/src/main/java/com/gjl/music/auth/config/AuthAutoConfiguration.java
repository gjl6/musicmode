package com.gjl.music.auth.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.context.annotation.Import;

/**
 * music-auth 模块自动配置入口。
 *
 * <p>在 WebMvc 之前加载，确保 SecurityFilterChain 先于 DispatcherServlet 注册。</p>
 *
 * <p>通过 {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * 自动发现。</p>
 */
@AutoConfiguration
@AutoConfigureBefore(name = "org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration")
@Import({SecurityConfig.class})
public class AuthAutoConfiguration {
}
