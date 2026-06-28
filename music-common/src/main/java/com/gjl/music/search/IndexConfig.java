package com.gjl.music.search;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.cn.smart.SmartChineseAnalyzer;
import org.apache.lucene.index.IndexWriterConfig;

import java.util.Map;

/**
 * Lucene 索引配置 — 字段定义、权重、分词器。
 *
 * <p>歌曲/专辑/艺术家共用同一索引，通过 type 字段区分。
 */
public final class IndexConfig {

    private IndexConfig() {}

    /** 索引字段名 */
    public static final String FIELD_ID = "id";

    /** 文档 ID 前缀，防止 song/album/artist 自增主键冲突 */
    public static final String DOC_ID_PREFIX_SONG = "song:";
    public static final String DOC_ID_PREFIX_ALBUM = "album:";
    public static final String DOC_ID_PREFIX_ARTIST = "artist:";

    public static String songDocId(Object id) { return DOC_ID_PREFIX_SONG + id; }
    public static String albumDocId(Object id) { return DOC_ID_PREFIX_ALBUM + id; }
    public static String artistDocId(Object id) { return DOC_ID_PREFIX_ARTIST + id; }

    /** 从存储的文档 ID 中提取原始 Long ID（兼容有无前缀两种格式） */
    public static Long parseStoredId(String storedId) {
        if (storedId == null) return null;
        int colon = storedId.indexOf(':');
        String numPart = colon > 0 ? storedId.substring(colon + 1) : storedId;
        try { return Long.valueOf(numPart); } catch (NumberFormatException e) { return null; }
    }
    public static final String FIELD_NAME = "name";       // 专辑名 / 艺术家名
    public static final String FIELD_TITLE = "title";     // 歌曲标题
    public static final String FIELD_ALBUM = "album";     // 歌曲所属专辑名 / 专辑名（同义复用）
    public static final String FIELD_ARTIST = "artist";   // 艺术家名
    public static final String FIELD_ALBUM_ARTIST = "albumArtist";
    public static final String FIELD_GENRE = "genre";
    public static final String FIELD_LYRICS = "lyrics";
    public static final String FIELD_COMMENT = "comment";
    public static final String FIELD_COUNTRY = "country";
    public static final String FIELD_YEAR = "year";
    public static final String FIELD_TYPE = "type";       // song/album/artist

    /** 歌曲字段权重 */
    public static final Map<String, Float> SONG_BOOST = Map.of(
            FIELD_TITLE, 10.0f,
            FIELD_ALBUM, 5.0f,
            FIELD_ARTIST, 3.0f,
            FIELD_ALBUM_ARTIST, 3.0f,
            FIELD_GENRE, 2.0f,
            FIELD_LYRICS, 1.5f,
            FIELD_COMMENT, 1.0f
    );

    /** 专辑字段权重 */
    public static final Map<String, Float> ALBUM_BOOST = Map.of(
            FIELD_NAME, 10.0f,
            FIELD_ALBUM, 10.0f,
            FIELD_ARTIST, 5.0f,
            FIELD_GENRE, 2.0f,
            FIELD_COMMENT, 1.0f
    );

    /** 艺术家字段权重 */
    public static final Map<String, Float> ARTIST_BOOST = Map.of(
            FIELD_NAME, 10.0f,
            FIELD_ARTIST, 10.0f,
            FIELD_COUNTRY, 3.0f,
            FIELD_COMMENT, 1.5f
    );

    /** 歌曲搜索字段 */
    public static final String[] SEARCH_FIELDS_SONG = {
            FIELD_TITLE, FIELD_ALBUM, FIELD_ARTIST, FIELD_ALBUM_ARTIST,
            FIELD_GENRE, FIELD_LYRICS, FIELD_COMMENT
    };

    /** 专辑搜索字段 */
    public static final String[] SEARCH_FIELDS_ALBUM = {
            FIELD_NAME, FIELD_ALBUM, FIELD_ARTIST, FIELD_GENRE, FIELD_COMMENT
    };

    /** 艺术家搜索字段 */
    public static final String[] SEARCH_FIELDS_ARTIST = {
            FIELD_NAME, FIELD_ARTIST, FIELD_COUNTRY, FIELD_COMMENT
    };

    /** 兼容旧代码（歌曲搜索字段） */
    public static final String[] SEARCH_FIELDS = SEARCH_FIELDS_SONG;
    /** 兼容旧代码（歌曲权重） */
    public static final Map<String, Float> FIELD_BOOST = SONG_BOOST;

    /** 创建 Analyzer 实例 */
    public static Analyzer createAnalyzer() {
        return new SmartChineseAnalyzer();
    }

    /** 创建 IndexWriter 配置 */
    public static IndexWriterConfig createWriterConfig() {
        Analyzer analyzer = createAnalyzer();
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        return config;
    }
}
