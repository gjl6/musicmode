package com.gjl.music.playback.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 流媒体播放配置属性。
 *
 * <p>所有属性前缀 {@code music.playback}，支持标准 application.yml 绑定。</p>
 */
@Data
@ConfigurationProperties(prefix = "music.playback")
public class PlaybackProperties {

    /** 转码配置 */
    private Transcoding transcoding = new Transcoding();

    /** 缓存配置 */
    private Cache cache = new Cache();

    @Data
    public static class Transcoding {
        /** 是否启用实时转码 */
        private boolean enabled = true;

        /** 默认目标格式 */
        private String defaultFormat = "mp3";

        /** 默认比特率（kbps） */
        private int defaultBitrate = 192;

        /** 全局最大并发转码数（0 = 不限制） */
        private int maxConcurrent = 4;

        /** 每用户最大并发转码数（0 = 不限制） */
        private int maxConcurrentPerUser = 2;
    }

    @Data
    public static class Cache {
        /** 转码缓存目录 */
        private String transcodingCacheDir = "data/transcoding-cache";

        /** 转码缓存上限（MB） */
        private int transcodingCacheMaxSizeMB = 2048;
    }

}
