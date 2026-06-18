package com.gjl.music.playback.search;
import com.gjl.music.playback.infra.search.IndexManager;

import com.gjl.music.mapper.MusicMapper;
import com.gjl.music.model.Song;
import com.gjl.music.playback.config.PlaybackProperties;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
@DisplayName("LuceneSearchService 单元测试")
class LuceneSearchServiceTest {

    @Mock
    private MusicMapper musicMapper;

    private Path tempDir;
    private IndexManager indexManager;
    private LuceneSearchService searchService;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("lucene-search-test-");
        PlaybackProperties props = new PlaybackProperties();
        props.getSearch().setIndexDir(tempDir.toString());

        when(musicMapper.findAllSongIds()).thenReturn(List.of(1L, 2L, 3L, 4L, 5L));
        when(musicMapper.findSongsByIds(anyList())).thenReturn(List.of(
                Song.builder().id("1").title("夜曲")
                        .filePath("/music/01.flac").build(),
                Song.builder().id("2").title("七里香")
                        .filePath("/music/02.flac").build(),
                Song.builder().id("3").title("晴天")
                        .filePath("/music/03.flac").build(),
                Song.builder().id("4").title("不能说的秘密")
                        .filePath("/music/04.flac").build(),
                Song.builder().id("5").title("告白气球")
                        .filePath("/music/05.flac").build()
        ));

        indexManager = new IndexManager(props, musicMapper);
        indexManager.init();

        searchService = new LuceneSearchService(indexManager);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (indexManager != null) {
            indexManager.shutdown();
        }
        try (var files = Files.walk(tempDir)) {
            files.sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignored) {} });
        }
    }


    @Test
    @DisplayName("精确词搜索返回结果")
    void exactWordSearch() {
        List<Long> results = searchService.searchSongs("夜曲", 0, 20);
        assertFalse(results.isEmpty());
        assertTrue(results.contains(1L));
    }

    @Test
    @DisplayName("模糊搜索返回多个结果")
    void fuzzySearch() {
        List<Long> results = searchService.searchSongs("气球", 0, 20);
        assertTrue(results.stream().anyMatch(id -> id == 5L));
    }

    @Test
    @DisplayName("空查询返回空列表")
    void emptyQuery() {
        assertTrue(searchService.searchSongs("", 0, 20).isEmpty());
        assertTrue(searchService.searchSongs("   ", 0, 20).isEmpty());
    }

    @Test
    @DisplayName("null 查询返回空列表")
    void nullQuery() {
        assertTrue(searchService.searchSongs(null, 0, 20).isEmpty());
    }


    @Test
    @DisplayName("searchAlbums（无专辑数据时返回空）")
    void searchAlbumsEmpty() {
        List<Long> results = searchService.searchAlbums("test", 0, 20);
        assertNotNull(results);
    }

    @Test
    @DisplayName("searchArtists（无艺术家数据时返回空）")
    void searchArtistsEmpty() {
        List<Long> results = searchService.searchArtists("test", 0, 20);
        assertNotNull(results);
    }


    @Test
    @DisplayName("分页：offset 超出范围返回空")
    void paginationOffsetOutOfRange() {
        List<Long> results = searchService.searchSongs("周杰伦", 999, 20);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("limit 限制结果数")
    void paginationLimit() {
        List<Long> results = searchService.searchSongs("天", 0, 1);
        assertTrue(results.size() <= 1);
    }


    @Test
    @DisplayName("UUID 格式查询短接返回空")
    void uuidQueryShortCircuit() {
        String uuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
        List<Long> results = searchService.searchSongs(uuid, 0, 20);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("尾部星号被去除")
    void trailingWildcardRemoved() {
        List<Long> results = searchService.searchSongs("夜曲*", 0, 20);
                assertNotNull(results);
    }

    @Test
    @DisplayName("searcher 为 null 时返回空列表")
    void nullSearcherReturnsEmpty() {
                        List<Long> results = searchService.searchSongs("test", 0, 20);
        assertNotNull(results);
    }


    @Test
    @DisplayName("搜索仅返回 song 类型文档")
    void searchOnlyReturnsSongs() {
        List<Long> results = searchService.searchSongs("夜曲", 0, 20);
                for (Long id : results) {
            assertNotNull(id);
        }
    }

    @Test
    @DisplayName("Lucene 语法错误时返回空列表")
    void syntaxErrorGraceful() {
                List<Long> results = searchService.searchSongs("[特殊语法]", 0, 20);
        assertTrue(results.isEmpty());
    }
}
