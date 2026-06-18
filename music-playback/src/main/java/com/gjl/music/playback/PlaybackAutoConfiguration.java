package com.gjl.music.playback;

import com.gjl.music.playback.config.PlaybackProperties;
import com.gjl.music.playback.infra.subsonic.SubsonicResponseBuilder;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;


@AutoConfiguration
@ComponentScan(basePackages = "com.gjl.music.playback")
@EnableConfigurationProperties(PlaybackProperties.class)
public class PlaybackAutoConfiguration {


    @Bean
    public SubsonicResponseBuilder subsonicResponseBuilder() {
        return new SubsonicResponseBuilder("1.0.0-SNAPSHOT", true);
    }

}
