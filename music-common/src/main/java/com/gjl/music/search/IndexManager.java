package com.gjl.music.search;

import com.gjl.music.dto.ArtistRef;
import com.gjl.music.dto.SongResult;
import com.gjl.music.mapper.AlbumMapper;
import com.gjl.music.mapper.ArtistMapper;
import com.gjl.music.mapper.LyricMapper;
import com.gjl.music.mapper.SongMapper;
import com.gjl.music.model.Album;
import com.gjl.music.model.Artist;
import com.gjl.music.model.Song;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Lucene 索引生命周期管理。
 *
 * <p>索引存储在磁盘（mmap 访问），OS page cache 自动缓存热数据。
 * 首次启动全量构建，后续启动增量差异更新。
 */
@Slf4j
@Service
public class IndexManager {

    private final Path indexDir;
    private final Path markerFile;
    private final SongMapper songMapper;
    private final LyricMapper lyricMapper;
    private final AlbumMapper albumMapper;
    private final ArtistMapper artistMapper;

    private FSDirectory directory;
    private IndexWriter writer;
    private IndexReader reader;
    private IndexSearcher searcher;
    private volatile long lastSyncTimestamp = 0;

    /** 跟踪实际写入的文档数（commit 时清零），用于条件更新时间戳 */
    private final AtomicInteger writeCount = new AtomicInteger(0);
    /** 首次构建标志：init() 后为 true，startupCheck() 中构建后置 false */
    private final AtomicBoolean initialBuildNeeded = new AtomicBoolean(false);
    /** 索引健康阈值（索引文档数 / DB 文档数），低于此值触发重建 */
    private final double healthThreshold;

    public IndexManager(@Value("${music.search.index-dir:data/search-index}") String indexDir,
                        @Value("${music.search.health-threshold:0.8}") double healthThreshold,
                        SongMapper songMapper,
                        LyricMapper lyricMapper,
                        AlbumMapper albumMapper,
                        ArtistMapper artistMapper) {
        this.indexDir = Path.of(indexDir);
        this.markerFile = this.indexDir.resolve(".sync-timestamp");
        this.healthThreshold = healthThreshold;
        this.songMapper = songMapper;
        this.lyricMapper = lyricMapper;
        this.albumMapper = albumMapper;
        this.artistMapper = artistMapper;
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(indexDir);
            directory = FSDirectory.open(indexDir);

            if (DirectoryReader.indexExists(directory)) {
                openExisting();
            } else {
                // 索引不存在 → 创建空索引（writer 打开即可），标记需要初始构建
                log.info("索引目录不存在，创建空索引: {}", indexDir);
                writer = new IndexWriter(directory, IndexConfig.createWriterConfig());
                writer.commit();
                refreshReader();
                initialBuildNeeded.set(true);
                lastSyncTimestamp = System.currentTimeMillis();
                writeSyncTimestamp(lastSyncTimestamp);
                log.info("空索引已创建，标记为待构建 (startupCheck 将在数据就绪后执行)");
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

    // ── 索引打开 / 构建 ──

    private void openExisting() throws IOException {
        log.info("打开已有索引: {}", indexDir);
        writer = new IndexWriter(directory, IndexConfig.createWriterConfig());
        refreshReader();
        // 恢复上次同步时间戳（catchUpSync 将在 startupCheck 中统一执行）
        lastSyncTimestamp = readSyncTimestamp();
        log.info("索引就绪: docs={}, size={}MB, lastSync={}",
                reader.numDocs(), indexSizeMB(),
                lastSyncTimestamp > 0 ? Instant.ofEpochMilli(lastSyncTimestamp).toString() : "无");
    }

    /** 全量构建索引（歌曲 + 专辑 + 艺术家） */
    public synchronized void buildFullIndex() {
        try {
            // 守卫：DB 为空时不构建，防止误删事件处理器已写入的文档
            long dbSongs = songMapper.countAllSongs();
            long dbAlbums = albumMapper.countAllAlbums();
            long dbArtists = artistMapper.countAllArtists();
            if (dbSongs == 0 && dbAlbums == 0 && dbArtists == 0) {
                log.info("DB 中暂无数据，跳过全量构建");
                return;
            }
            log.info("开始全量构建索引... (DB: songs={}, albums={}, artists={})",
                    dbSongs, dbAlbums, dbArtists);
            long start = System.currentTimeMillis();

            // 删除旧索引
            if (writer != null && writer.isOpen()) {
                writer.close();
            }
            writer = new IndexWriter(directory, IndexConfig.createWriterConfig());
            writer.deleteAll();

            int totalDocs = 0;

            // ── 1. 索引歌曲 ──
            List<Long> songIds = songMapper.findAllSongIds();
            if (!songIds.isEmpty()) {
                int total = songIds.size();
                int batchSize = 500;
                int progressInterval = Math.max(batchSize * 20, total / 10);

                for (int i = 0; i < total; i += batchSize) {
                    int end = Math.min(i + batchSize, total);
                    List<Long> batch = songIds.subList(i, end);

                    List<Song> songs = songMapper.findSongsByIds(batch);
                    Map<Long, String> genreMap = buildGenreMap(batch);
                    Map<Long, String> lyricMap = buildLyricMap(batch);
                    Map<Long, List<String>> artistNameMap = buildArtistNameMap(batch);

                    for (Song song : songs) {
                        Long longId = toLong(song.getId());
                        String genre = genreMap.getOrDefault(longId, null);
                        String lyrics = lyricMap.getOrDefault(longId, null);
                        List<String> allArtistNames = artistNameMap.getOrDefault(longId, List.of());
                        indexSong(song, genre, lyrics, allArtistNames);
                    }

                    if (i > 0 && i % progressInterval == 0) {
                        log.info("歌曲索引进度: {}/{} ({}%)", i, total, i * 100 / total);
                    }
                }
                totalDocs += songIds.size();
                log.info("歌曲索引完成: {}", songIds.size());
            }

            // ── 2. 索引专辑 ──
            List<Long> albumIds = albumMapper.findAllAlbumIds();
            if (!albumIds.isEmpty()) {
                List<Album> albums = albumMapper.findAlbumsByIds(albumIds);
                for (Album album : albums) {
                    try {
                        indexAlbum(album);
                    } catch (IOException e) {
                        log.warn("索引专辑失败: id={}", album.getId(), e);
                    }
                }
                totalDocs += albumIds.size();
                log.info("专辑索引完成: {}", albumIds.size());
            }

            // ── 3. 索引艺术家 ──
            List<Long> artistIds = artistMapper.findAllArtistIds();
            if (!artistIds.isEmpty()) {
                List<Artist> artists = artistMapper.findArtistsByIds(artistIds);
                for (Artist artist : artists) {
                    try {
                        indexArtist(artist);
                    } catch (IOException e) {
                        log.warn("索引艺术家失败: id={}", artist.getId(), e);
                    }
                }
                totalDocs += artistIds.size();
                log.info("艺术家索引完成: {}", artistIds.size());
            }

            writer.commit();
            refreshReader();
            lastSyncTimestamp = System.currentTimeMillis();
            writeSyncTimestamp(lastSyncTimestamp);

            long elapsed = System.currentTimeMillis() - start;
            log.info("全量索引构建完成: totalDocs={}, readerDocs={}, time={}s, size={}MB",
                    totalDocs, reader.numDocs(), elapsed / 1000, indexSizeMB());
        } catch (Exception e) {
            log.error("全量索引构建失败", e);
            throw new RuntimeException("索引构建失败", e);
        }
    }

    // ── 增量更新 ──

    /** 添加或更新单个文档 */
    public void indexSong(Song song) throws IOException {
        indexSong(song, null, null, List.of());
    }

    /** 添加或更新单个歌曲文档（带预查的流派和歌词） */
    public void indexSong(Song song, String genre, String lyrics) throws IOException {
        indexSong(song, genre, lyrics, List.of());
    }

    /** 添加或更新单个歌曲文档（带全部艺术家名，用于批量建索引） */
    public synchronized void indexSong(Song song, String genre, String lyrics, List<String> allArtistNames) throws IOException {
        Document doc = buildSongDocument(song, genre, lyrics, allArtistNames);
        writer.updateDocument(new Term(IndexConfig.FIELD_ID, IndexConfig.songDocId(song.getId())), doc);
        writeCount.incrementAndGet();
    }

    /** 添加或更新单个专辑文档 */
    public synchronized void indexAlbum(Album album) throws IOException {
        Document doc = buildAlbumDocument(album);
        writer.updateDocument(new Term(IndexConfig.FIELD_ID, IndexConfig.albumDocId(album.getId())), doc);
        writeCount.incrementAndGet();
    }

    /** 添加或更新单个艺术家文档 */
    public synchronized void indexArtist(Artist artist) throws IOException {
        Document doc = buildArtistDocument(artist);
        writer.updateDocument(new Term(IndexConfig.FIELD_ID, IndexConfig.artistDocId(artist.getId())), doc);
        writeCount.incrementAndGet();
    }

    // ── 按 ID 重索引（IndexEventListener 使用，从 DB 回读最新数据后写入索引）──

    /** 重索引单首歌曲（从 DB 回读最新数据）。synchronized 防止与 buildFullIndex() 竞态。 */
    public synchronized void reindexSong(Long songId) throws IOException {
        List<Song> songs = songMapper.findSongsByIds(List.of(songId));
        if (songs == null || songs.isEmpty()) {
            deleteSong(songId);
            return;
        }
        Song song = songs.getFirst();
        Map<Long, String> genreMap = buildGenreMap(List.of(songId));
        Map<Long, String> lyricMap = buildLyricMap(List.of(songId));
        Map<Long, List<String>> artistNameMap = buildArtistNameMap(List.of(songId));
        indexSong(song,
                genreMap.getOrDefault(songId, null),
                lyricMap.getOrDefault(songId, null),
                artistNameMap.getOrDefault(songId, List.of()));
    }

    /** 重索引单张专辑（从 DB 回读最新数据） */
    public synchronized void reindexAlbum(Long albumId) throws IOException {
        List<Album> albums = albumMapper.findAlbumsByIds(List.of(albumId));
        if (albums == null || albums.isEmpty()) {
            deleteAlbum(albumId);
            return;
        }
        indexAlbum(albums.getFirst());
    }

    /** 重索引单个艺术家（从 DB 回读最新数据） */
    public synchronized void reindexArtist(Long artistId) throws IOException {
        List<Artist> artists = artistMapper.findArtistsByIds(List.of(artistId));
        if (artists == null || artists.isEmpty()) {
            deleteArtist(artistId);
            return;
        }
        indexArtist(artists.getFirst());
    }

    // ── 删除（单个 + 批量）──

    /** 删除歌曲文档 */
    public synchronized void deleteSong(Long songId) throws IOException {
        writer.deleteDocuments(new Term(IndexConfig.FIELD_ID, IndexConfig.songDocId(songId)));
        writeCount.incrementAndGet();
    }

    /** 删除专辑文档 */
    public synchronized void deleteAlbum(Long albumId) throws IOException {
        writer.deleteDocuments(new Term(IndexConfig.FIELD_ID, IndexConfig.albumDocId(albumId)));
        writeCount.incrementAndGet();
    }

    /** 删除艺术家文档 */
    public synchronized void deleteArtist(Long artistId) throws IOException {
        writer.deleteDocuments(new Term(IndexConfig.FIELD_ID, IndexConfig.artistDocId(artistId)));
        writeCount.incrementAndGet();
    }

    /** 批量删除歌曲 */
    public synchronized void deleteSongs(List<Long> songIds) throws IOException {
        if (songIds == null || songIds.isEmpty()) return;
        writer.deleteDocuments(new TermInSetQuery(IndexConfig.FIELD_ID,
                songIds.stream().map(id -> new BytesRef(IndexConfig.songDocId(id))).toList()));
        writeCount.addAndGet(songIds.size());
    }

    /** 批量删除专辑 */
    public synchronized void deleteAlbums(List<Long> albumIds) throws IOException {
        if (albumIds == null || albumIds.isEmpty()) return;
        writer.deleteDocuments(new TermInSetQuery(IndexConfig.FIELD_ID,
                albumIds.stream().map(id -> new BytesRef(IndexConfig.albumDocId(id))).toList()));
        writeCount.addAndGet(albumIds.size());
    }

    /** 批量删除艺术家 */
    public synchronized void deleteArtists(List<Long> artistIds) throws IOException {
        if (artistIds == null || artistIds.isEmpty()) return;
        writer.deleteDocuments(new TermInSetQuery(IndexConfig.FIELD_ID,
                artistIds.stream().map(id -> new BytesRef(IndexConfig.artistDocId(id))).toList()));
        writeCount.addAndGet(artistIds.size());
    }

    /** 提交并刷新 reader。仅在有实际写入时更新时间戳，避免产生"增量盲区" */
    public synchronized void commit() throws IOException {
        if (writer != null && writer.isOpen()) {
            writer.commit();
            refreshReader();
            // 只有在有实际写入时才推进时间戳
            int count = writeCount.getAndSet(0);
            if (count > 0) {
                lastSyncTimestamp = System.currentTimeMillis();
                writeSyncTimestamp(lastSyncTimestamp);
            }
        }
    }

    /** 增量同步：将 lastSyncTimestamp 之后更新的歌曲/专辑/艺术家写入索引，并清理孤立文档 */
    public synchronized void syncIncremental() {
        if (lastSyncTimestamp <= 0) {
            log.debug("跳过增量同步：未初始化同步时间戳");
            return;
        }
        try {
            LocalDateTime since = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(lastSyncTimestamp), ZoneId.systemDefault());

            int total = 0;

            // 歌曲增量
            List<Long> songIds = songMapper.findSongIdsUpdatedAfter(since);
            if (songIds != null && !songIds.isEmpty()) {
                syncSongs(songIds);
                total += songIds.size();
            }

            // 专辑增量（使用 findAlbumIdsUpdatedAfter 而非全量重写）
            List<Long> albumIds = albumMapper.findAlbumIdsUpdatedAfter(since);
            if (albumIds != null && !albumIds.isEmpty()) {
                syncAlbums(albumIds);
                total += albumIds.size();
            }

            // 艺术家增量（同上）
            List<Long> artistIds = artistMapper.findArtistIdsUpdatedAfter(since);
            if (artistIds != null && !artistIds.isEmpty()) {
                syncArtists(artistIds);
                total += artistIds.size();
            }

            // 孤立文档清理（索引中有但 DB 中已删除的文档）
            int purged = purgeOrphanedDocuments();

            commit();
            if (total > 0 || purged > 0) {
                log.debug("增量索引同步: {} 变更, {} 孤立文档清理", total, purged);
            }
        } catch (Exception e) {
            log.warn("增量同步失败: {}", e.getMessage());
        }
    }

    /** 追补停机期间变更（仅在 openExisting 启动时调用） */
    private int catchUpSync(long sinceTimestamp) {
        try {
            LocalDateTime since = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(sinceTimestamp), ZoneId.systemDefault());
            int total = 0;

            List<Long> songIds = songMapper.findSongIdsUpdatedAfter(since);
            if (songIds != null && !songIds.isEmpty()) {
                syncSongs(songIds);
                total += songIds.size();
            }

            // 专辑/艺术家增量追补
            List<Long> albumIds = albumMapper.findAlbumIdsUpdatedAfter(since);
            if (albumIds != null && !albumIds.isEmpty()) {
                syncAlbums(albumIds);
                total += albumIds.size();
            }

            List<Long> artistIds = artistMapper.findArtistIdsUpdatedAfter(since);
            if (artistIds != null && !artistIds.isEmpty()) {
                syncArtists(artistIds);
                total += artistIds.size();
            }

            purgeOrphanedDocuments();
            commit();
            return total;
        } catch (Exception e) {
            log.warn("追补同步失败: {}", e.getMessage());
            return 0;
        }
    }

    /** 同步一批歌曲到索引（批量查 Song + genre + lyric 后逐条写入） */
    private void syncSongs(List<Long> ids) {
        int batchSize = 500;
        for (int i = 0; i < ids.size(); i += batchSize) {
            int end = Math.min(i + batchSize, ids.size());
            List<Long> batch = ids.subList(i, end);

            List<Song> songs = songMapper.findSongsByIds(batch);
            Map<Long, String> genreMap = buildGenreMap(batch);
            Map<Long, String> lyricMap = buildLyricMap(batch);
            Map<Long, List<String>> artistNameMap = buildArtistNameMap(batch);

            for (Song song : songs) {
                Long longId = toLong(song.getId());
                String genre = genreMap.getOrDefault(longId, null);
                String lyrics = lyricMap.getOrDefault(longId, null);
                List<String> allArtistNames = artistNameMap.getOrDefault(longId, List.of());
                try {
                    indexSong(song, genre, lyrics, allArtistNames);
                } catch (IOException e) {
                    log.warn("索引歌曲失败: id={}", song.getId(), e);
                }
            }
        }
    }

    /** 增量索引一批专辑 */
    private void syncAlbums(List<Long> ids) {
        try {
            List<Album> albums = albumMapper.findAlbumsByIds(ids);
            for (Album album : albums) {
                try {
                    indexAlbum(album);
                } catch (IOException e) {
                    log.warn("索引专辑失败: id={}", album.getId(), e);
                }
            }
        } catch (Exception e) {
            log.warn("专辑索引同步失败: {}", e.getMessage());
        }
    }

    /** 增量索引一批艺术家 */
    private void syncArtists(List<Long> ids) {
        try {
            List<Artist> artists = artistMapper.findArtistsByIds(ids);
            for (Artist artist : artists) {
                try {
                    indexArtist(artist);
                } catch (IOException e) {
                    log.warn("索引艺术家失败: id={}", artist.getId(), e);
                }
            }
        } catch (Exception e) {
            log.warn("艺术家索引同步失败: {}", e.getMessage());
        }
    }

    // ── 启动检查 + 健康监控 ──

    /**
     * 启动时检查：在数据就绪后（ApplicationReadyEvent 延迟）调用。
     * 首次启动或索引为空 → 全量构建；非首次 → 健康检查 + 追补停机变更。
     */
    public synchronized void startupCheck() {
        try {
            boolean needsFullBuild = initialBuildNeeded.getAndSet(false);
            if (needsFullBuild) {
                log.info("startupCheck: 首次启动，执行全量构建...");
                buildFullIndex();
                // 如果 DB 为空，buildFullIndex 会跳过，恢复标志等待后续重试
                if (reader == null || reader.numDocs() == 0) {
                    long dbTotal = songMapper.countAllSongs() + albumMapper.countAllAlbums() + artistMapper.countAllArtists();
                    if (dbTotal == 0) {
                        initialBuildNeeded.set(true);
                        log.info("startupCheck: DB 暂无数据，推迟全量构建至定期健康检查");
                    }
                }
                return;
            }
            if (reader == null || reader.numDocs() == 0) {
                log.info("startupCheck: 索引为空，执行全量构建...");
                buildFullIndex();
                return;
            }
            // 非首次：健康检查
            HealthStatus health = healthCheck();
            if (!health.healthy()) {
                log.warn("startupCheck: 索引不健康，触发自动重建 — {}", health);
                buildFullIndex();
                return;
            }
            log.info("startupCheck: 索引健康，{}", health);
            // 追补停机期间的变更
            if (lastSyncTimestamp > 0) {
                int caught = catchUpSync(lastSyncTimestamp);
                if (caught > 0) log.info("追补停机期间变更: {} docs", caught);
            }
        } catch (Exception e) {
            log.error("startupCheck 失败", e);
        }
    }

    /** 索引健康状态 */
    public record HealthStatus(boolean songHealthy, boolean albumHealthy, boolean artistHealthy,
                               long dbSongs, long idxSongs, long dbAlbums, long idxAlbums,
                               long dbArtists, long idxArtists) {
        public boolean healthy() { return songHealthy && albumHealthy && artistHealthy; }
        @Override public String toString() {
            return String.format("songs(idx=%d/db=%d %.0f%%) albums(idx=%d/db=%d %.0f%%) artists(idx=%d/db=%d %.0f%%)",
                    idxSongs, dbSongs, dbSongs > 0 ? 100.0 * idxSongs / dbSongs : 100,
                    idxAlbums, dbAlbums, dbAlbums > 0 ? 100.0 * idxAlbums / dbAlbums : 100,
                    idxArtists, dbArtists, dbArtists > 0 ? 100.0 * idxArtists / dbArtists : 100);
        }
    }

    /**
     * 健康检查：对比索引文档数与 DB 记录数。
     * 阈值由配置 music.search.health-threshold 控制（默认 0.8 = 80%）。
     */
    public HealthStatus healthCheck() {
        long dbSongs = songMapper.countAllSongs();
        long dbAlbums = albumMapper.countAllAlbums();
        long dbArtists = artistMapper.countAllArtists();

        long idxSongs = countDocsByType("song");
        long idxAlbums = countDocsByType("album");
        long idxArtists = countDocsByType("artist");

        boolean songHealthy = dbSongs == 0 || idxSongs >= dbSongs * healthThreshold;
        boolean albumHealthy = dbAlbums == 0 || idxAlbums >= dbAlbums * healthThreshold;
        boolean artistHealthy = dbArtists == 0 || idxArtists >= dbArtists * healthThreshold;

        return new HealthStatus(songHealthy, albumHealthy, artistHealthy,
                dbSongs, idxSongs, dbAlbums, idxAlbums, dbArtists, idxArtists);
    }

    /**
     * 健康检查 + 自动修复：在定期增量同步前调用。
     * 如果健康检查不通过 → 触发全量重建。
     */
    public synchronized void healthCheckAndRepair() {
        HealthStatus health;
        try {
            health = healthCheck();
        } catch (Exception e) {
            log.warn("健康检查执行失败: {}", e.getMessage());
            return;
        }
        if (!health.healthy()) {
            log.warn("索引健康检查不通过，触发自动重建 — {}", health);
            try {
                buildFullIndex();
            } catch (Exception e) {
                log.error("自动重建失败", e);
            }
        }
    }

    /**
     * 按类型统计索引中的文档数。
     * 遍历 reader 中所有文档，按 type 字段计数（适用于小规模索引，O(n)）。
     */
    public int countDocsByType(String type) {
        if (reader == null) return 0;
        try {
            int count = 0;
            int nullType = 0;
            int maxDoc = reader.numDocs();
            for (int i = 0; i < maxDoc; i++) {
                Document doc = reader.document(i);
                String docType = doc.get(IndexConfig.FIELD_TYPE);
                if (type.equals(docType)) {
                    count++;
                } else if (docType == null) {
                    nullType++;
                }
            }
            if (nullType > 0) {
                log.warn("countDocsByType({}): total={}, matched={}, nullType={}, otherType={}",
                        type, maxDoc, count, nullType, maxDoc - count - nullType);
            }
            return count;
        } catch (IOException e) {
            log.warn("countDocsByType({}) 失败: {}", type, e.getMessage());
            return -1;
        }
    }

    // ── 搜索 ──

    public IndexSearcher getSearcher() {
        return searcher;
    }

    // ── 索引信息 ──

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

    // ── private helpers ──

    private void refreshReader() throws IOException {
        if (reader != null) {
            reader.close();
        }
        reader = DirectoryReader.open(writer);
        searcher = new IndexSearcher(reader);
    }

    private Document buildSongDocument(Song song, String genre, String lyrics, List<String> allArtistNames) {
        Document doc = new Document();
        doc.add(new StringField(IndexConfig.FIELD_ID, IndexConfig.songDocId(song.getId()), Field.Store.YES));
        doc.add(new StringField(IndexConfig.FIELD_TYPE, "song", Field.Store.YES));

        addTextField(doc, IndexConfig.FIELD_TITLE,        song.getTitle());
        addTextField(doc, IndexConfig.FIELD_ALBUM,        song.getAlbumName());
        // 艺术家：优先使用批量查询的全部艺术家，回退到主艺术家（保持 findSongsByIds 中 sort_order=0 的行为）
        if (allArtistNames != null && !allArtistNames.isEmpty()) {
            for (String name : allArtistNames) {
                addTextField(doc, IndexConfig.FIELD_ARTIST,       name);
                addTextField(doc, IndexConfig.FIELD_ALBUM_ARTIST, name);
            }
        } else {
            addTextField(doc, IndexConfig.FIELD_ARTIST,       song.getArtistName());
            addTextField(doc, IndexConfig.FIELD_ALBUM_ARTIST, song.getArtistName());
        }
        addTextField(doc, IndexConfig.FIELD_GENRE,        genre);
        addTextField(doc, IndexConfig.FIELD_LYRICS,       lyrics);
        addTextField(doc, IndexConfig.FIELD_COMMENT,      song.getExtraTags());
        addTextField(doc, IndexConfig.FIELD_YEAR,         song.getYear());

        return doc;
    }

    private Document buildAlbumDocument(Album album) {
        Document doc = new Document();
        doc.add(new StringField(IndexConfig.FIELD_ID, IndexConfig.albumDocId(album.getId()), Field.Store.YES));
        doc.add(new StringField(IndexConfig.FIELD_TYPE, "album", Field.Store.YES));

        addTextField(doc, IndexConfig.FIELD_NAME,   album.getAlbumName());
        addTextField(doc, IndexConfig.FIELD_ALBUM,  album.getAlbumName());
        addTextField(doc, IndexConfig.FIELD_ARTIST, null);  // 由外层查 artist 名填入（可选）
        addTextField(doc, IndexConfig.FIELD_YEAR,   album.getAlbumYear() != null ? album.getAlbumYear().toString() : null);
        addTextField(doc, IndexConfig.FIELD_GENRE,  null);  // 专辑流派可选
        addTextField(doc, IndexConfig.FIELD_COMMENT, album.getIntroduction());

        return doc;
    }

    private Document buildArtistDocument(Artist artist) {
        Document doc = new Document();
        doc.add(new StringField(IndexConfig.FIELD_ID, IndexConfig.artistDocId(artist.getId()), Field.Store.YES));
        doc.add(new StringField(IndexConfig.FIELD_TYPE, "artist", Field.Store.YES));

        addTextField(doc, IndexConfig.FIELD_NAME,    artist.getArtistName());
        addTextField(doc, IndexConfig.FIELD_ARTIST,  artist.getArtistName());
        addTextField(doc, IndexConfig.FIELD_COUNTRY, artist.getCountry());
        addTextField(doc, IndexConfig.FIELD_COMMENT, artist.getIntroduction());

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

    /** 批量查询流派映射 (songId → styleName，每首歌取第一个风格) */
    // ── 批量预查辅助 ──

    /** 批量查询全部艺术家名 (songId → 所有艺术家名列表，按 sort_order 排序) */
    private Map<Long, List<String>> buildArtistNameMap(List<Long> songIds) {
        if (songIds.isEmpty()) return Map.of();
        Map<Long, List<ArtistRef>> refMap = SongResult.groupArtistsBySongId(
                songMapper.findSongArtistsBySongIds(songIds));
        Map<Long, List<String>> result = new HashMap<>();
        refMap.forEach((sid, refs) -> result.put(sid,
                refs.stream().map(ArtistRef::name).filter(n -> n != null && !n.isBlank()).collect(Collectors.toList())));
        return result;
    }

    private Map<Long, String> buildGenreMap(List<Long> songIds) {
        List<Map<String, Object>> rows = songMapper.findGenresBySongIds(songIds);
        Map<Long, String> map = new HashMap<>();
        if (rows == null) return map;
        for (Map<String, Object> row : rows) {
            Long songId = toLong(row.get("SONGID"));
            String styleName = (String) row.get("STYLENAME");
            if (songId != null && styleName != null) {
                map.putIfAbsent(songId, styleName);
            }
        }
        return map;
    }

    /** 批量查询歌词映射 (songId → lyricContent，截断至500字符) */
    private Map<Long, String> buildLyricMap(List<Long> songIds) {
        List<Map<String, Object>> rows = lyricMapper.findLyricsBySongIds(songIds);
        Map<Long, String> map = new HashMap<>();
        if (rows == null) return map;
        for (Map<String, Object> row : rows) {
            Long songId = toLong(row.get("SONGID"));
            String content = (String) row.get("LYRICCONTENT");
            if (songId != null && content != null && !content.isEmpty()) {
                map.put(songId, content.length() > 500 ? content.substring(0, 500) : content);
            }
        }
        return map;
    }

    /** 安全转换 Object → Long（兼容 H2 返回大写 key 和不同类型的数值） */
    private static Long toLong(Object val) {
        if (val == null) return null;
        if (val instanceof Long l) return l;
        if (val instanceof Number n) return n.longValue();
        try { return Long.valueOf(val.toString()); } catch (NumberFormatException e) { return null; }
    }

    // ── 同步时间戳持久化（靠文件而非 DB，避免循环依赖）──

    private void writeSyncTimestamp(long timestamp) {
        try {
            Files.writeString(markerFile, String.valueOf(timestamp), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.debug("写入同步时间戳失败: {}", e.getMessage());
        }
    }

    private long readSyncTimestamp() {
        try {
            if (Files.exists(markerFile)) {
                return Long.parseLong(Files.readString(markerFile, StandardCharsets.UTF_8).trim());
            }
        } catch (IOException | NumberFormatException e) {
            log.debug("读取同步时间戳失败: {}", e.getMessage());
        }
        return 0;
    }

    // ── 孤立文档清理 ──

    /**
     * 清理孤立文档：对比索引中 ID 和 DB 中 ID，删除索引中存在但 DB 中已被删除的文档。
     *
     * @return 清理的文档数
     */
    private int purgeOrphanedDocuments() {
        int total = 0;
        try {
            // 对比时使用 Set 交集差运算
            Set<Long> dbSongIds = new HashSet<>(songMapper.findAllSongIds());
            total += purgeByType("song", dbSongIds);

            Set<Long> dbAlbumIds = new HashSet<>(albumMapper.findAllAlbumIds());
            total += purgeByType("album", dbAlbumIds);

            Set<Long> dbArtistIds = new HashSet<>(artistMapper.findAllArtistIds());
            total += purgeByType("artist", dbArtistIds);
        } catch (Exception e) {
            log.warn("孤立文档清理失败: {}", e.getMessage());
        }
        if (total > 0) {
            log.info("孤立文档清理: {} 个", total);
        }
        return total;
    }

    /**
     * 清理指定类型的孤立文档。
     *
     * @param type   实体类型（song / album / artist）
     * @param dbIds  DB 中存在的 ID 集合
     * @return 清理的文档数
     */
    private int purgeByType(String type, Set<Long> dbIds) throws IOException {
        // 读取索引中该类型的所有 ID（文档 ID 已带前缀，用 parseStoredId 解析）
        Set<Long> indexedIds = new HashSet<>();
        IndexReader r = DirectoryReader.open(writer);
        try {
            for (int i = 0; i < r.numDocs(); i++) {
                Document doc = r.document(i);
                String docType = doc.get(IndexConfig.FIELD_TYPE);
                if (type.equals(docType)) {
                    Long id = IndexConfig.parseStoredId(doc.get(IndexConfig.FIELD_ID));
                    if (id != null) {
                        indexedIds.add(id);
                    }
                }
            }
        } finally {
            r.close();
        }

        // 索引中有但 DB 中没有 → 孤立文档
        indexedIds.removeAll(dbIds);
        if (indexedIds.isEmpty()) return 0;

        // 删除时需要重新加上类型前缀
        final String prefix = switch (type) {
            case "song" -> IndexConfig.DOC_ID_PREFIX_SONG;
            case "album" -> IndexConfig.DOC_ID_PREFIX_ALBUM;
            case "artist" -> IndexConfig.DOC_ID_PREFIX_ARTIST;
            default -> "";
        };
        List<BytesRef> orphanRefs = indexedIds.stream()
                .map(id -> new BytesRef(prefix + id))
                .toList();
        writer.deleteDocuments(new TermInSetQuery(IndexConfig.FIELD_ID, orphanRefs));
        return orphanRefs.size();
    }
}
