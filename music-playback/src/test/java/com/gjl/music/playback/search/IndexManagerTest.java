package com.gjl.music.playback.search;
import com.gjl.music.playback.infra.search.IndexManager;

import com.gjl.music.mapper.MusicMapper;
import com.gjl.music.model.Song;
import com.gjl.music.playback.config.PlaybackProperties;
import org.apache.lucene.document.Document;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
@DisplayName("IndexManager 单元测试")
class IndexManagerTest {

    @Mock
    private MusicMapper musicMapper;

    private Path tempDir;
    private PlaybackProperties properties;
    private IndexManager indexManager;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("lucene-test-");
        properties = new PlaybackProperties();
        properties.getSearch().setIndexDir(tempDir.toString());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (indexManager != null) {
            indexManager.shutdown();
        }
                try (var files = Files.walk(tempDir)) {
            files.sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignored) {} });
        }
    }

    @Test
    @DisplayName("init 无歌曲时成功创建空索引")
    void initEmptyIndex() {
        when(musicMapper.findAllSongIds()).thenReturn(List.of());

        indexManager = new IndexManager(properties, musicMapper);
        indexManager.init();

        assertEquals(0, indexManager.getNumDocs());
        assertNotNull(indexManager.getSearcher());
        assertTrue(indexManager.indexSizeMB() >= 0);
    }

    @Test
    @DisplayName("buildFullIndex 索引歌曲")
    void buildFullIndexWithSongs() {
        when(musicMapper.findAllSongIds()).thenReturn(List.of(1L, 2L, 3L));
        when(musicMapper.findSongsByIds(anyList())).thenReturn(List.of(
                Song.builder().id("1").title("夜曲").filePath("/music/01.flac").build(),
                Song.builder().id("2").title("七里香").filePath("/music/02.flac").build(),
                Song.builder().id("3").title("晴天").filePath("/music/03.flac").build()
        ));

        indexManager = new IndexManager(properties, musicMapper);
        indexManager.init();

        assertEquals(3, indexManager.getNumDocs());
        assertNotNull(indexManager.getSearcher());
    }

    @Test
    @DisplayName("indexSong 添加或更新文档")
    void indexSong() throws Exception {
        when(musicMapper.findAllSongIds()).thenReturn(List.of());
        indexManager = new IndexManager(properties, musicMapper);
        indexManager.init();
        assertEquals(0, indexManager.getNumDocs());

        Song song = Song.builder().id("99").title("测试歌曲").filePath("/music/test.flac").build();
        indexManager.indexSong(song);
        indexManager.commit();

        assertEquals(1, indexManager.getNumDocs());
    }

    @Test
    @DisplayName("deleteSong 删除文档")
    void deleteSong() throws Exception {
        when(musicMapper.findAllSongIds()).thenReturn(List.of(1L, 2L));
        when(musicMapper.findSongsByIds(anyList())).thenReturn(List.of(
                Song.builder().id("1").title("夜曲").filePath("/music/01.flac").build(),
                Song.builder().id("2").title("七里香").filePath("/music/02.flac").build()
        ));

        indexManager = new IndexManager(properties, musicMapper);
        indexManager.init();
        assertEquals(2, indexManager.getNumDocs());

        indexManager.deleteSong(1L);
        indexManager.commit();

        assertEquals(1, indexManager.getNumDocs());
    }

    @Test
    @DisplayName("updateSong 覆盖已存在文档")
    void updateSong() throws Exception {
        when(musicMapper.findAllSongIds()).thenReturn(List.of(1L));
        when(musicMapper.findSongsByIds(anyList())).thenReturn(List.of(
                Song.builder().id("1").title("原始标题").filePath("/music/01.flac").build()
        ));

        indexManager = new IndexManager(properties, musicMapper);
        indexManager.init();
        assertEquals(1, indexManager.getNumDocs());

                Song updated = Song.builder().id("1").title("更新后标题").filePath("/music/01.flac").build();
        indexManager.indexSong(updated);
        indexManager.commit();

        assertEquals(1, indexManager.getNumDocs());
    }

    @Test
    @DisplayName("getSearcher 返回可用 searcher")
    void getSearcher() {
        when(musicMapper.findAllSongIds()).thenReturn(List.of());
        indexManager = new IndexManager(properties, musicMapper);
        indexManager.init();

        IndexSearcher searcher = indexManager.getSearcher();
        assertNotNull(searcher);
    }

    @Test
    @DisplayName("init 失败不抛出异常（优雅降级）")
    void initFailureGraceful() {
                Path readOnlyDir = tempDir.resolve("readonly");
        try {
            Files.createDirectories(readOnlyDir);
            readOnlyDir.toFile().setReadOnly();
            properties.getSearch().setIndexDir(readOnlyDir.resolve("sub").toString());
        } catch (Exception ignored) {}

        when(musicMapper.findAllSongIds()).thenReturn(List.of());
        indexManager = new IndexManager(properties, musicMapper);

                assertDoesNotThrow(() -> indexManager.init());
    }

    @Test
    @DisplayName("syncIncremental 正常处理空变更")
    void syncIncrementalEmpty() {
        when(musicMapper.findAllSongIds()).thenReturn(List.of());
        indexManager = new IndexManager(properties, musicMapper);
        indexManager.init();

        assertDoesNotThrow(() -> indexManager.syncIncremental());
    }

    @Test
    @DisplayName("indexSizeMB 对空索引返回 >=0")
    void indexSizeMB() {
        when(musicMapper.findAllSongIds()).thenReturn(List.of());
        indexManager = new IndexManager(properties, musicMapper);
        indexManager.init();

        long sizeMB = indexManager.indexSizeMB();
        assertTrue(sizeMB >= 0);
    }

    @Test
    @DisplayName("shutdown 安全关闭（不抛异常）")
    void shutdownSafe() {
        when(musicMapper.findAllSongIds()).thenReturn(List.of());
        indexManager = new IndexManager(properties, musicMapper);
        indexManager.init();

        assertDoesNotThrow(() -> indexManager.shutdown());
                assertDoesNotThrow(() -> indexManager.shutdown());
    }
}
