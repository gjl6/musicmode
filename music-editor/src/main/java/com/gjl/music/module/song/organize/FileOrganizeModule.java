package com.gjl.music.module.song.organize;

import com.gjl.music.model.*;
import com.gjl.music.module.Module;

import java.util.List;

/**
 * 文件整理模块 —— 按元数据层级重组文件目录结构。
 *
 * <p>通过 {@link OrganizeField} 定义支持的元数据字段，
 * 每个字段可以从 {@link MusicMetadata} 中解析对应值。
 */
public interface FileOrganizeModule extends Module {

    /** 配置键：操作模式 "move" / "copy" */
    String KEY_MODE = "mode";

    /** 配置键：目标根目录（可选，为空时使用源文件所在目录） */
    String KEY_TARGET_ROOT = "targetRoot";

    /** 配置键：目录层级列表 */
    String KEY_LEVELS = "levels";

    /** 配置键：单层字段名 */
    String KEY_FIELD = "field";

    /** 缺失元数据时的默认占位值 */
    String UNKNOWN = "Unknown";

    /** 移动模式 */
    String MODE_MOVE = "move";

    /** 复制模式 */
    String MODE_COPY = "copy";

    /**
     * 支持的元数据字段枚举，用于目录层级配置。
     * 每个枚举值定义了从 {@link MusicMetadata} 中提取值的方式。
     */
    enum OrganizeField {

        ARTIST("artist", "Artist") {
            @Override
            public String resolve(MusicMetadata meta) {
                var artists = meta.getImmutableArtists();
                return artists.isEmpty() ? null : artists.getFirst().getArtistName();
            }
        },

        ALBUM("album", "Album") {
            @Override
            public String resolve(MusicMetadata meta) {
                var albums = meta.getImmutableAlbums();
                return albums.isEmpty() ? null : albums.getFirst().getAlbumName();
            }
        },

        TITLE("title", "Title") {
            @Override
            public String resolve(MusicMetadata meta) {
                var songs = meta.getImmutableSongs();
                return songs.isEmpty() ? null : songs.getFirst().getTitle();
            }
        },

        YEAR("year", "Year") {
            @Override
            public String resolve(MusicMetadata meta) {
                var songs = meta.getImmutableSongs();
                return songs.isEmpty() ? null : songs.getFirst().getYear();
            }
        },

        FORMAT("format", "Format") {
            @Override
            public String resolve(MusicMetadata meta) {
                var songs = meta.getImmutableSongs();
                return songs.isEmpty() ? null : songs.getFirst().getFileFormat();
            }
        },

        STYLE("style", "Style") {
            @Override
            public String resolve(MusicMetadata meta) {
                var styles = meta.getImmutableStyles();
                return styles.isEmpty() ? null : styles.getFirst().getStyleName();
            }
        },

        LANGUAGE("language", "Language") {
            @Override
            public String resolve(MusicMetadata meta) {
                var songs = meta.getImmutableSongs();
                return songs.isEmpty() ? null : songs.getFirst().getLanguage();
            }
        },

        TRACK_NUMBER("trackNumber", "Track #") {
            @Override
            public String resolve(MusicMetadata meta) {
                var songs = meta.getImmutableSongs();
                if (songs.isEmpty()) return null;
                Integer tn = songs.getFirst().getTrackNumber();
                return (tn != null && tn > 0) ? String.valueOf(tn) : null;
            }
        },

        DISC_NUMBER("discNumber", "Disc #") {
            @Override
            public String resolve(MusicMetadata meta) {
                var songs = meta.getImmutableSongs();
                if (songs.isEmpty()) return null;
                Integer dn = songs.getFirst().getDiscNumber();
                return (dn != null && dn > 0) ? String.valueOf(dn) : null;
            }
        },

        COMPOSER("composer", "Composer") {
            @Override
            public String resolve(MusicMetadata meta) {
                var songs = meta.getImmutableSongs();
                return songs.isEmpty() ? null : songs.getFirst().getComposer();
            }
        },

        LYRICIST("lyricist", "Lyricist") {
            @Override
            public String resolve(MusicMetadata meta) {
                var songs = meta.getImmutableSongs();
                return songs.isEmpty() ? null : songs.getFirst().getLyricist();
            }
        },

        ALBUM_YEAR("albumYear", "Album Year") {
            @Override
            public String resolve(MusicMetadata meta) {
                var albums = meta.getImmutableAlbums();
                if (albums.isEmpty()) return null;
                Integer ay = albums.getFirst().getAlbumYear();
                return (ay != null && ay > 0) ? String.valueOf(ay) : null;
            }
        },

        COMPANY("company", "Company") {
            @Override
            public String resolve(MusicMetadata meta) {
                var albums = meta.getImmutableAlbums();
                return albums.isEmpty() ? null : albums.getFirst().getCompany();
            }
        };

        private final String configKey;
        private final String displayName;

        OrganizeField(String configKey, String displayName) {
            this.configKey = configKey;
            this.displayName = displayName;
        }

        public String getConfigKey() { return configKey; }
        public String getDisplayName() { return displayName; }

        /**
         * 从 MusicMetadata 中解析本字段对应的值。
         * 返回 null 表示元数据缺失。
         */
        public abstract String resolve(MusicMetadata meta);

        /**
         * 根据配置 key 查找对应的枚举值（大小写不敏感）。
         */
        public static OrganizeField fromConfigKey(String key) {
            if (key == null) return null;
            for (OrganizeField f : values()) {
                if (f.configKey.equalsIgnoreCase(key)) return f;
            }
            return null;
        }
    }
}
