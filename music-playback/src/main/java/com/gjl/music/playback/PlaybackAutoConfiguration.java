package com.gjl.music.playback;

import com.gjl.music.playback.config.PlaybackProperties;
import com.gjl.music.playback.infra.subsonic.SubsonicResponseBuilder;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

/**
 * music-playback 模块自动配置入口。
 *
 * <p>通过 {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * 自动发现。组件扫描范围：{@code com.gjl.music.playback} 包及其子包。
 */
@AutoConfiguration
@ComponentScan(basePackages = "com.gjl.music.playback")
@EnableConfigurationProperties(PlaybackProperties.class)
public class PlaybackAutoConfiguration {

    /** Subsonic XML/JSON 响应构建器 */
    @Bean
    public SubsonicResponseBuilder subsonicResponseBuilder() {
        return new SubsonicResponseBuilder("2.0.0", true);
    }

}
