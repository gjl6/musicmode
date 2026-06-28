package com.gjl.music.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 自动迁移旧 enrich config key 到实体类型前缀命名。
 *
 * <p>迁移规则：{@code enrich.{provider}.{param}} → {@code enrich.{entity}.{provider}.{param}}
 * 旧值覆盖写入新 key（首次迁移时用户尚未操作新 key，覆盖安全）。旧 key 不删除。
 *
 * <p>通过 marker key {@code enrich.migration.v2_entity_prefix} 保证仅执行一次。
 */
@Slf4j
@Component
public class EnrichConfigMigration {

    private static final String MARKER_KEY = "enrich.migration.v2_entity_prefix";

    private static final String[] PROVIDERS = {
            "qqmusic", "netease", "kugou", "kuwo", "itunes", "musicbrainz", "migu",
            "baidubaike", "wikipedia"
    };

    private static final String[] ENTITIES = {"song", "artist", "album"};

    /** 仅 Song 实体存在的 provider（kugou, kuwo, migu）不复制到 artist/album */
    private static final boolean isSongOnly(String provider) {
        return "kugou".equals(provider) || "kuwo".equals(provider) || "migu".equals(provider);
    }

    /** 仅 Artist 实体存在的 provider（baidubaike, wikipedia）不复制到 song/album */
    private static final boolean isArtistOnly(String provider) {
        return "baidubaike".equals(provider) || "wikipedia".equals(provider);
    }

    private static final String[] PARAMS = {
            "rate_limit_ms", "max_concurrent", "timeout_seconds",
            "rate_limit_retries", "rate_limit_backoff_ms",
            "search_url", "album_url", "lyric_url", "detail_url",
            "lookup_url", "release_url", "song_url",
            "user_agent", "cover_tpl", "cover_art_tpl", "cover_cdn", "cover_cdn_mv",
            "referer", "csrf", "csrf_token",
            "lyric_search_url", "lyric_download_url", "artist_search_url", "artist_detail_url",
            "base_url", "api_url", "warmup_url",
            "max_intro_len", "rate_limit_code", "metadata_tags"
    };

    private final ConfigService configService;

    public EnrichConfigMigration(ConfigService configService) {
        this.configService = configService;
    }

    @PostConstruct
    public void migrate() {
        String markerValue = configService.getString(MARKER_KEY, null);
        if (markerValue != null && !markerValue.isBlank()) {
            log.debug("EnrichConfigMigration: already migrated, marker={}", markerValue);
            return;
        }

        int migrated = 0;
        for (String provider : PROVIDERS) {
            for (String entity : ENTITIES) {
                // artist-only provider 不复制到 song/album
                if (isArtistOnly(provider) && !"artist".equals(entity)) continue;
                // song-only provider 不复制到 artist/album
                if (isSongOnly(provider) && !"song".equals(entity)) continue;

                for (String param : PARAMS) {
                    String oldKey = "enrich." + provider + "." + param;
                    String newKey = "enrich." + entity + "." + provider + "." + param;

                    // 旧 key 有值则覆盖写入新 key（首次迁移时用户尚未操作新 key，覆盖安全）
                    String oldValue = configService.getString(oldKey, null);
                    if (oldValue != null && !oldValue.isBlank()) {
                        configService.updateValue(newKey, oldValue);
                        migrated++;
                        log.info("EnrichConfigMigration: {} → {}", oldKey, newKey);
                    }
                }
            }
        }

        configService.updateValue(MARKER_KEY, "true");
        log.info("EnrichConfigMigration: completed, {} keys migrated", migrated);
    }
}
