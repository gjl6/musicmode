package com.gjl.music.playback.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;


@Data
@ConfigurationProperties(prefix = "music.playback")
public class PlaybackProperties {


    private Transcoding transcoding = new Transcoding();


    private Cache cache = new Cache();


    private Search search = new Search();

    @Data
    public static class Transcoding {

        private boolean enabled = true;


        private String defaultFormat = "mp3";


        private int defaultBitrate = 192;


        private int maxConcurrent = 4;


        private int maxConcurrentPerUser = 2;
    }

    @Data
    public static class Cache {

        private String transcodingCacheDir = "data/transcoding-cache";


        private int transcodingCacheMaxSizeMB = 2048;
    }

    @Data
    public static class Search {

        private String indexDir = "data/search-index";
    }
}
