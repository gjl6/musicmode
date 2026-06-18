package com.gjl.music.playback.infra.search;

import com.gjl.music.mapper.MusicMapper;
import com.gjl.music.model.Album;
import com.gjl.music.model.Artist;
import com.gjl.music.model.Song;
import com.gjl.music.model.Lyric;
import com.gjl.music.playback.config.PlaybackProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;


@Slf4j
@Service
public class IndexManager {

    private final Path indexDir;
    private final MusicMapper musicMapper;

    private FSDirectory directory;
    private IndexWriter writer;
    private IndexReader reader;
    private IndexSearcher searcher;
    private volatile long lastSyncTimestamp = 0;

    public IndexManager(PlaybackProperties properties, MusicMapper musicMapper) {
        this.indexDir = Path.of(properties.getSearch().getIndexDir());
        this.musicMapper = musicMapper;
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(indexDir);
            directory = FSDirectory.open(indexDir);

            if (DirectoryReader.indexExists(directory)) {
                openExisting();
            } else {
                buildFullIndex();
            }
        } catch (Exception e) {
            log.warn("Lucene 索引初始化失败，搜索功能将不可用: {} (indexDir={})",
                    e.getMessage(), indexDir);
                    }
    }

    @PreDestroy
    public void shutdown() {
        try {
            if (writer != null && writer.isOpen()) {
                writer.close();
            }
            if (directory != null) {
                directory.close();
            }
        } catch (IOException e) {
            log.warn("索引关闭异常: {}", e.getMessage());
        }
    }


    private void openExisting() throws IOException {
        log.info("打开已有索引: {}", indexDir);
        writer = new IndexWriter(directory, IndexConfig.createWriterConfig());
        refreshReader();
        lastSyncTimestamp = System.currentTimeMillis();
        log.info("索引就绪: docs={}, size={}MB",
                reader.numDocs(), indexSizeMB());
    }


    public synchronized void buildFullIndex() {
        try {
            log.info("开始全量构建索引...");
            long start = System.currentTimeMillis();

                        if (writer != null && writer.isOpen()) {
                writer.close();
            }
            writer = new IndexWriter(directory, IndexConfig.createWriterConfig());
            writer.deleteAll();

                        List<Long> songIds = musicMapper.findAllSongIds();
            if (songIds.isEmpty()) {
                log.info("无歌曲，跳过索引构建");
                writer.commit();
                refreshReader();
                return;
            }

            int batchSize = 500;
            for (int i = 0; i < songIds.size(); i += batchSize) {
                int end = Math.min(i + batchSize, songIds.size());
                List<Long> batch = songIds.subList(i, end);
                List<Song> songs = musicMapper.findSongsByIds(batch);
                for (Song song : songs) {
                    indexSong(song);
                }
                if ((i / batchSize) % 10 == 0) {
                    log.debug("索引进度: {}/{}", end, songIds.size());
                }
            }

            writer.commit();
            refreshReader();
            lastSyncTimestamp = System.currentTimeMillis();

            long elapsed = System.currentTimeMillis() - start;
            log.info("全量索引构建完成: songs={}, time={}ms, size={}MB",
                    reader.numDocs(), elapsed, indexSizeMB());
        } catch (Exception e) {
            log.error("全量索引构建失败", e);
            throw new RuntimeException("索引构建失败", e);
        }
    }


    public void indexSong(Song song) throws IOException {
        Document doc = buildDocument(song);
        writer.updateDocument(new Term(IndexConfig.FIELD_ID, song.getId()), doc);
    }


    public void deleteSong(Long songId) throws IOException {
        writer.deleteDocuments(new Term(IndexConfig.FIELD_ID, String.valueOf(songId)));
    }


    public synchronized void commit() throws IOException {
        if (writer != null && writer.isOpen()) {
            writer.commit();
            refreshReader();
            lastSyncTimestamp = System.currentTimeMillis();
        }
    }


    public void syncIncremental() {
        try {
            java.time.LocalDateTime since = java.time.LocalDateTime.now()
                    .minusMinutes(10);
            List<Long> ids = musicMapper.findSongIdsUpdatedAfter(since);
            if (ids.isEmpty()) return;

            List<Song> songs = musicMapper.findSongsByIds(ids);
            for (Song song : songs) {
                indexSong(song);
            }
            commit();
            log.debug("增量索引同步: {} songs", songs.size());
        } catch (Exception e) {
            log.warn("增量同步失败: {}", e.getMessage());
        }
    }


    public IndexSearcher getSearcher() {
        return searcher;
    }


    public long getNumDocs() {
        return reader != null ? reader.numDocs() : 0;
    }

    public long indexSizeMB() {
        try {
            long size = 0;
            if (Files.exists(indexDir)) {
                for (Path f : Files.walk(indexDir).filter(Files::isRegularFile).toList()) {
                    size += Files.size(f);
                }
            }
            return size / (1024 * 1024);
        } catch (IOException e) {
            return 0;
        }
    }


    private void refreshReader() throws IOException {
        if (reader != null) {
            reader.close();
        }
        reader = DirectoryReader.open(writer);
        searcher = new IndexSearcher(reader);
    }

    private Document buildDocument(Song song) {
        Document doc = new Document();
        doc.add(new StringField(IndexConfig.FIELD_ID, song.getId(), Field.Store.YES));
        doc.add(new StringField(IndexConfig.FIELD_TYPE, "song", Field.Store.NO));

        addTextField(doc, IndexConfig.FIELD_TITLE, song.getTitle());

        return doc;
    }

    private void addTextField(Document doc, String name, String value) {
        if (value != null && !value.isBlank()) {
            FieldType ft = new FieldType();
            ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS);
            ft.setStored(false);
            ft.setTokenized(true);
            ft.setOmitNorms(false);
            ft.freeze();
            doc.add(new Field(name, value, ft));
        }
    }
}
