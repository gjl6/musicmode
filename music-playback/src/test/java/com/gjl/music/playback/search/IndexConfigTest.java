package com.gjl.music.playback.search;
import com.gjl.music.playback.infra.search.IndexConfig;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.cn.smart.SmartChineseAnalyzer;
import org.apache.lucene.index.IndexWriterConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;


@DisplayName("IndexConfig 单元测试")
class IndexConfigTest {

    @Test
    @DisplayName("FIELD_BOOST 包含所有搜索字段的正确权重")
    void fieldBoosts() {
        assertEquals(10.0f, IndexConfig.FIELD_BOOST.get(IndexConfig.FIELD_TITLE));
        assertEquals(5.0f, IndexConfig.FIELD_BOOST.get(IndexConfig.FIELD_ALBUM));
        assertEquals(3.0f, IndexConfig.FIELD_BOOST.get(IndexConfig.FIELD_ARTIST));
        assertEquals(3.0f, IndexConfig.FIELD_BOOST.get(IndexConfig.FIELD_ALBUM_ARTIST));
        assertEquals(2.0f, IndexConfig.FIELD_BOOST.get(IndexConfig.FIELD_GENRE));
        assertEquals(1.5f, IndexConfig.FIELD_BOOST.get(IndexConfig.FIELD_LYRICS));
        assertEquals(1.0f, IndexConfig.FIELD_BOOST.get(IndexConfig.FIELD_COMMENT));
    }

    @Test
    @DisplayName("SEARCH_FIELDS 包含正确字段顺序")
    void searchFields() {
        assertEquals(7, IndexConfig.SEARCH_FIELDS.length);
        assertEquals(IndexConfig.FIELD_TITLE, IndexConfig.SEARCH_FIELDS[0]);
        assertEquals(IndexConfig.FIELD_ALBUM, IndexConfig.SEARCH_FIELDS[1]);
        assertEquals(IndexConfig.FIELD_ARTIST, IndexConfig.SEARCH_FIELDS[2]);
        assertEquals(IndexConfig.FIELD_ALBUM_ARTIST, IndexConfig.SEARCH_FIELDS[3]);
        assertEquals(IndexConfig.FIELD_GENRE, IndexConfig.SEARCH_FIELDS[4]);
        assertEquals(IndexConfig.FIELD_LYRICS, IndexConfig.SEARCH_FIELDS[5]);
        assertEquals(IndexConfig.FIELD_COMMENT, IndexConfig.SEARCH_FIELDS[6]);
    }

    @Test
    @DisplayName("createAnalyzer 返回 SmartChineseAnalyzer")
    void createAnalyzer() {
        Analyzer analyzer = IndexConfig.createAnalyzer();
        assertNotNull(analyzer);
        assertTrue(analyzer instanceof SmartChineseAnalyzer);
    }

    @Test
    @DisplayName("createWriterConfig 创建 CREATE_OR_APPEND 模式")
    void createWriterConfig() {
        IndexWriterConfig config = IndexConfig.createWriterConfig();
        assertNotNull(config);
        assertNotNull(config.getAnalyzer());
        assertEquals(IndexWriterConfig.OpenMode.CREATE_OR_APPEND, config.getOpenMode());
    }

    @Test
    @DisplayName("常量值正确")
    void constants() {
        assertEquals("id", IndexConfig.FIELD_ID);
        assertEquals("title", IndexConfig.FIELD_TITLE);
        assertEquals("album", IndexConfig.FIELD_ALBUM);
        assertEquals("artist", IndexConfig.FIELD_ARTIST);
        assertEquals("albumArtist", IndexConfig.FIELD_ALBUM_ARTIST);
        assertEquals("genre", IndexConfig.FIELD_GENRE);
        assertEquals("lyrics", IndexConfig.FIELD_LYRICS);
        assertEquals("comment", IndexConfig.FIELD_COMMENT);
        assertEquals("year", IndexConfig.FIELD_YEAR);
        assertEquals("type", IndexConfig.FIELD_TYPE);
    }
}
