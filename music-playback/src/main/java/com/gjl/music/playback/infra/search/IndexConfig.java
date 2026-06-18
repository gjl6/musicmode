package com.gjl.music.playback.infra.search;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.cn.smart.SmartChineseAnalyzer;
import org.apache.lucene.index.IndexWriterConfig;

import java.util.Map;


public final class IndexConfig {

    private IndexConfig() {}


    public static final String FIELD_ID = "id";
    public static final String FIELD_TITLE = "title";
    public static final String FIELD_ALBUM = "album";
    public static final String FIELD_ARTIST = "artist";
    public static final String FIELD_ALBUM_ARTIST = "albumArtist";
    public static final String FIELD_GENRE = "genre";
    public static final String FIELD_LYRICS = "lyrics";
    public static final String FIELD_COMMENT = "comment";
    public static final String FIELD_YEAR = "year";
    public static final String FIELD_TYPE = "type";


    public static final Map<String, Float> FIELD_BOOST = Map.of(
            FIELD_TITLE, 10.0f,
            FIELD_ALBUM, 5.0f,
            FIELD_ARTIST, 3.0f,
            FIELD_ALBUM_ARTIST, 3.0f,
            FIELD_GENRE, 2.0f,
            FIELD_LYRICS, 1.5f,
            FIELD_COMMENT, 1.0f
    );


    public static final String[] SEARCH_FIELDS = {
            FIELD_TITLE, FIELD_ALBUM, FIELD_ARTIST, FIELD_ALBUM_ARTIST,
            FIELD_GENRE, FIELD_LYRICS, FIELD_COMMENT
    };


    public static Analyzer createAnalyzer() {
        return new SmartChineseAnalyzer();
    }


    public static IndexWriterConfig createWriterConfig() {
        Analyzer analyzer = createAnalyzer();
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        return config;
    }
}
