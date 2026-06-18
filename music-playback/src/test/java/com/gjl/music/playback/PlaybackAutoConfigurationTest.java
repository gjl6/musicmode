package com.gjl.music.playback;
import com.gjl.music.playback.infra.security.SubsonicAuthFilter;
import com.gjl.music.playback.infra.subsonic.SubsonicResponseBuilder;

import com.gjl.music.playback.config.PlaybackProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;


@DisplayName("PlaybackAutoConfiguration 自动配置测试")
class PlaybackAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(MinimalConfig.class);

    @EnableConfigurationProperties(PlaybackProperties.class)
    static class MinimalConfig {}

    @Test
    @DisplayName("PlaybackProperties 绑定默认值")
    void playbackPropertiesDefaults() {
        contextRunner.run(ctx -> {
            assertThat(ctx).hasSingleBean(PlaybackProperties.class);
            PlaybackProperties props = ctx.getBean(PlaybackProperties.class);

            assertThat(props.getTranscoding().isEnabled()).isTrue();
            assertThat(props.getTranscoding().getDefaultFormat()).isEqualTo("mp3");
            assertThat(props.getTranscoding().getDefaultBitrate()).isEqualTo(192);
            assertThat(props.getTranscoding().getMaxConcurrent()).isEqualTo(4);
            assertThat(props.getTranscoding().getMaxConcurrentPerUser()).isEqualTo(2);

            assertThat(props.getCache().getTranscodingCacheDir()).isEqualTo("data/transcoding-cache");
            assertThat(props.getCache().getTranscodingCacheMaxSizeMB()).isEqualTo(2048);

            assertThat(props.getSearch().getIndexDir()).isEqualTo("data/search-index");
        });
    }

    @Test
    @DisplayName("自定义属性绑定")
    void customProperties() {
        contextRunner
                .withPropertyValues(
                        "music.playback.transcoding.enabled=false",
                        "music.playback.transcoding.default-format=opus",
                        "music.playback.transcoding.max-concurrent=8",
                        "music.playback.cache.transcoding-cache-dir=/tmp/cache",
                        "music.playback.cache.transcoding-cache-max-size-mb=1024",
                        "music.playback.search.index-dir=/var/lib/music-index"
                )
                .run(ctx -> {
                    PlaybackProperties props = ctx.getBean(PlaybackProperties.class);

                    assertThat(props.getTranscoding().isEnabled()).isFalse();
                    assertThat(props.getTranscoding().getDefaultFormat()).isEqualTo("opus");
                    assertThat(props.getTranscoding().getMaxConcurrent()).isEqualTo(8);
                    assertThat(props.getCache().getTranscodingCacheDir()).isEqualTo("/tmp/cache");
                    assertThat(props.getCache().getTranscodingCacheMaxSizeMB()).isEqualTo(1024);
                    assertThat(props.getSearch().getIndexDir()).isEqualTo("/var/lib/music-index");
                });
    }

    @Test
    @DisplayName("SubsonicResponseBuilder 手动创建正确")
    void subsonicResponseBuilder() {
        var builder = new com.gjl.music.playback.subsonic.SubsonicResponseBuilder("1.0.0-TEST", true);
        var result = builder.buildOk(null);
        assertThat(result.get("status")).isEqualTo("ok");
        assertThat(result.get("serverVersion")).isEqualTo("1.0.0-TEST");
        assertThat(result.get("openSubsonic")).isEqualTo(true);
    }

    @Test
    @DisplayName("SubsonicAuthFilter 手动创建正确")
    void subsonicAuthFilter() {
        var filter = new com.gjl.music.playback.subsonic.SubsonicAuthFilter(null);
        assertThat(filter).isNotNull();
    }
}
