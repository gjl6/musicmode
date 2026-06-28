package com.gjl.music.playback.controller;

import com.gjl.music.config.ConfigService;
import com.gjl.music.mapper.AlbumMapper;
import com.gjl.music.mapper.ArtistMapper;
import com.gjl.music.mapper.LyricMapper;
import com.gjl.music.mapper.SongMapper;
import com.gjl.music.model.Album;
import com.gjl.music.model.Artist;
import com.gjl.music.model.Song;
import com.gjl.music.dto.AlbumResult;
import com.gjl.music.dto.ArtistRef;
import com.gjl.music.dto.ArtistResult;
import com.gjl.music.dto.SongResult;
import com.gjl.music.playback.mapper.BrowseMapper;
import com.gjl.music.search.IndexManager;
import com.gjl.music.search.SearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.function.Function;

/**
 * 全文搜索 REST API。
 *
 * <p>提供歌曲/专辑/艺术家的 Lucene 全文搜索、索引状态查询和全量重建。
 */
@Slf4j
@RestController
@RequestMapping("/api/search")
@PreAuthorize("hasAuthority('music:read')")
public class SearchController {

    private final SearchService searchService;
    private final IndexManager indexManager;
    private final SongMapper songMapper;
    private final AlbumMapper albumMapper;
    private final ArtistMapper artistMapper;
    private final LyricMapper lyricMapper;
    private final BrowseMapper browseMapper;
    private final ConfigService configService;

    public SearchController(SearchService searchService,
                            IndexManager indexManager,
                            SongMapper songMapper,
                            AlbumMapper albumMapper,
                            ArtistMapper artistMapper,
                            LyricMapper lyricMapper,
                            BrowseMapper browseMapper,
                            ConfigService configService) {
        this.searchService = searchService;
        this.indexManager = indexManager;
        this.songMapper = songMapper;
        this.albumMapper = albumMapper;
        this.artistMapper = artistMapper;
        this.lyricMapper = lyricMapper;
        this.browseMapper = browseMapper;
        this.configService = configService;
    }

    /**
     * 全文搜索。
     *
     * @param query  搜索关键词
     * @param type   搜索类型：song / album / artist
     * @param offset 分页偏移
     * @param limit  返回数量上限
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> search(
            @RequestParam String query,
            @RequestParam(defaultValue = "song") String type,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit) {

        // ★ 关键字直通 DB：查询中包含配置关键字 → 跳过 Lucene，直接 SQL LIKE
        String dbKeywordsCsv = configService.getString("music.search.db-keywords", "");
        String matchedKeyword = findKeywordInQuery(query, dbKeywordsCsv);
        if (matchedKeyword != null) {
            String cleaned = query.replace(matchedKeyword, "").trim();
            log.info("[SearchAPI] 关键字命中直通DB: query='{}', keyword='{}', cleaned='{}', type={}",
                    query, matchedKeyword, cleaned, type);
            if (cleaned.isEmpty()) {
                return ResponseEntity.ok(Map.of("type", type, "results", List.of(), "total", 0));
            }
            return searchDirectDb(cleaned, type, offset, limit);
        }

        return switch (type) {
            case "song" -> {
                List<Long> ids = searchService.searchSongs(query, offset, limit);
                List<Song> rawSongs = ids.isEmpty()
                        ? List.of()
                        : reorderByIds(songMapper.findSongsByIds(ids), ids, Song::getId);
                Map<Long, List<ArtistRef>> artistMap = groupArtistsForSongs(rawSongs);
                String joinSep = configService.getString("music.artist.join-separator", " & ");
                List<SongResult> songs = rawSongs.stream()
                        .map(s -> SongResult.enrichArtists(toSongResult(s), artistMap, joinSep))
                        .toList();
                yield ResponseEntity.ok(Map.of(
                        "type", "song",
                        "results", (Object) songs,
                        "total", songs.size()
                ));
            }
            case "album" -> {
                List<Long> ids = searchService.searchAlbums(query, offset, limit);
                List<AlbumResult> albums = ids.isEmpty()
                        ? List.of()
                        : reorderByIds(albumMapper.findAlbumsByIds(ids), ids, Album::getId)
                            .stream().map(SearchController::toAlbumResult).toList();
                yield ResponseEntity.ok(Map.of(
                        "type", "album",
                        "results", (Object) albums,
                        "total", albums.size()
                ));
            }
            case "artist" -> {
                List<Long> ids = searchService.searchArtists(query, offset, limit);
                List<ArtistResult> artists = ids.isEmpty()
                        ? List.of()
                        : reorderByIds(artistMapper.findArtistsByIds(ids), ids, Artist::getId)
                            .stream().map(SearchController::toArtistResult).toList();
                yield ResponseEntity.ok(Map.of(
                        "type", "artist",
                        "results", (Object) artists,
                        "total", artists.size()
                ));
            }
            case "lyric" -> {
                List<Long> ids = lyricMapper.searchLyricSongIds(query, limit);
                List<Song> rawSongs = ids.isEmpty()
                        ? List.of()
                        : songMapper.findSongsByIds(ids);
                Map<Long, List<ArtistRef>> artistMap = groupArtistsForSongs(rawSongs);
                List<SongResult> songs = rawSongs.stream()
                        .map(s -> SongResult.enrichArtists(toSongResult(s), artistMap))
                        .toList();
                yield ResponseEntity.ok(Map.of(
                        "type", "lyric",
                        "results", (Object) songs,
                        "total", songs.size()
                ));
            }
            default -> ResponseEntity.badRequest()
                    .body(Map.of("error", "无效的搜索类型: " + type,
                                 "validTypes", List.of("song", "album", "artist", "lyric")));
        };
    }

    // ── DTO 映射（委托 common DTO 静态工厂 + toBuilder 补充模块特定字段）──

    private static SongResult toSongResult(Song s) {
        return SongResult.fromSong(s).toBuilder()
                .coverArt("song-" + s.getId())
                .build();
    }

    private static AlbumResult toAlbumResult(Album a) {
        return AlbumResult.fromAlbum(a).toBuilder()
                .artist(a.getArtistName())
                .coverArt("album-" + a.getId())
                .build();
    }

    private static ArtistResult toArtistResult(Artist a) {
        return ArtistResult.fromArtist(a).toBuilder()
                .coverArt("artist-" + a.getId())
                .build();
    }

    // ── helpers ──

    private Map<Long, List<ArtistRef>> groupArtistsForSongs(List<Song> songs) {
        if (songs == null || songs.isEmpty()) return Map.of();
        List<Long> songIds = songs.stream()
                .map(s -> Long.parseLong(s.getId()))
                .distinct().toList();
        return SongResult.groupArtistsBySongId(
                songMapper.findSongArtistsBySongIds(songIds));
    }

    /** 按 Lucene 返回的 ID 顺序重排水化结果，保持 BM25 相关性排序 */
    private static <T> List<T> reorderByIds(List<T> rows, List<Long> idOrder,
                                            Function<T, String> idFn) {
        if (rows.size() <= 1) return rows;
        Map<String, Integer> rank = new HashMap<>();
        for (int i = 0; i < idOrder.size(); i++) {
            rank.put(String.valueOf(idOrder.get(i)), i);
        }
        List<T> sorted = new ArrayList<>(rows);
        sorted.sort(Comparator.comparingInt(r -> {
            Integer pos = rank.get(idFn.apply(r));
            return pos != null ? pos : Integer.MAX_VALUE;
        }));
        return sorted;
    }

    // ── 索引管理 ──

    /**
     * 全量重建索引。
     */
    @PostMapping("/reindex")
    @PreAuthorize("hasAuthority('music:write')")
    public ResponseEntity<Map<String, Object>> reindex() {
        long start = System.currentTimeMillis();
        try {
            indexManager.buildFullIndex();
            long elapsed = System.currentTimeMillis() - start;
            return ResponseEntity.ok(Map.of(
                    "status", "ok",
                    "docs", indexManager.getNumDocs(),
                    "sizeMB", indexManager.indexSizeMB(),
                    "timeMs", elapsed
            ));
        } catch (Exception e) {
            log.error("索引重建失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    /**
     * 索引状态查询（含按类型统计数 + DB 对比 + 健康状态）。
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        IndexManager.HealthStatus health = indexManager.healthCheck();
        return ResponseEntity.ok(Map.of(
                "numDocs", indexManager.getNumDocs(),
                "sizeMB", indexManager.indexSizeMB(),
                "healthy", health.healthy(),
                "songs", Map.of("index", health.idxSongs(), "db", health.dbSongs()),
                "albums", Map.of("index", health.idxAlbums(), "db", health.dbAlbums()),
                "artists", Map.of("index", health.idxArtists(), "db", health.dbArtists())
        ));
    }

    // ══════════════════════════════════════════════════
    // 关键字直通 DB 辅助方法
    // ══════════════════════════════════════════════════

    /** 检查查询是否包含配置的 DB 直通关键字，返回匹配到的关键字（null=未匹配） */
    private static String findKeywordInQuery(String query, String dbKeywordsCsv) {
        if (query == null || dbKeywordsCsv == null || dbKeywordsCsv.isBlank()) return null;
        String lower = query.toLowerCase();
        for (String kw : dbKeywordsCsv.split(",")) {
            String trimmed = kw.trim();
            if (!trimmed.isEmpty() && lower.contains(trimmed.toLowerCase())) {
                return trimmed;
            }
        }
        return null;
    }

    /** 直接 DB SQL LIKE 搜索（跳过 Lucene） */
    private ResponseEntity<Map<String, Object>> searchDirectDb(
            String query, String type, int offset, int limit) {
        return switch (type) {
            case "song" -> {
                List<Song> songs = browseMapper.searchSongsByKeyword(query, offset, limit);
                Map<Long, List<ArtistRef>> artistMap = groupArtistsForSongs(songs);
                String joinSep = configService.getString("music.artist.join-separator", " & ");
                List<SongResult> results = songs.stream()
                        .map(s -> SongResult.enrichArtists(toSongResult(s), artistMap, joinSep))
                        .toList();
                log.info("[SearchAPI] DB直通结果: type=song, query='{}', results={}", query, results.size());
                yield ResponseEntity.ok(Map.of("type", "song", "results", (Object) results, "total", results.size()));
            }
            case "album" -> {
                List<Album> albums = browseMapper.searchAlbumsByKeyword(query, offset, limit);
                List<AlbumResult> results = albums.stream()
                        .map(SearchController::toAlbumResult).toList();
                log.info("[SearchAPI] DB直通结果: type=album, query='{}', results={}", query, results.size());
                yield ResponseEntity.ok(Map.of("type", "album", "results", (Object) results, "total", results.size()));
            }
            case "artist" -> {
                List<Artist> artists = browseMapper.searchArtistsByKeyword(query, offset, limit);
                List<ArtistResult> results = artists.stream()
                        .map(SearchController::toArtistResult).toList();
                log.info("[SearchAPI] DB直通结果: type=artist, query='{}', results={}", query, results.size());
                yield ResponseEntity.ok(Map.of("type", "artist", "results", (Object) results, "total", results.size()));
            }
            case "lyric" -> {
                // DB 直通无歌词全文索引，回退到歌曲关键词搜索
                List<Song> songs = browseMapper.searchSongsByKeyword(query, offset, limit);
                Map<Long, List<ArtistRef>> artistMap = groupArtistsForSongs(songs);
                List<SongResult> results = songs.stream()
                        .map(s -> SongResult.enrichArtists(toSongResult(s), artistMap))
                        .toList();
                log.info("[SearchAPI] DB直通结果: type=lyric, query='{}', results={}", query, results.size());
                yield ResponseEntity.ok(Map.of("type", "lyric", "results", (Object) results, "total", results.size()));
            }
            default -> ResponseEntity.badRequest()
                    .body(Map.of("error", "无效的搜索类型: " + type));
        };
    }
}
