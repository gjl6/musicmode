package com.gjl.music.playback.service.impl;

import com.gjl.music.config.ConfigService;
import com.gjl.music.dto.AlbumResult;
import com.gjl.music.dto.ArtistRef;
import com.gjl.music.dto.ArtistResult;
import com.gjl.music.dto.SongResult;
import com.gjl.music.mapper.AlbumMapper;
import com.gjl.music.mapper.ArtistMapper;
import com.gjl.music.mapper.LyricMapper;
import com.gjl.music.mapper.SongMapper;
import com.gjl.music.model.Album;
import com.gjl.music.model.Artist;
import com.gjl.music.model.Song;
import com.gjl.music.playback.mapper.BrowseMapper;
import com.gjl.music.playback.mapper.PlayCountMapper;
import com.gjl.music.playback.infra.subsonic.SubsonicRequestParams;
import com.gjl.music.playback.infra.subsonic.SubsonicUserBridge;
import com.gjl.music.search.IndexManager;
import com.gjl.music.playback.model.Playlist;
import com.gjl.music.playback.service.PlayCountService;
import com.gjl.music.playback.service.PlaylistService;
import com.gjl.music.playback.service.StreamingService;
import com.gjl.music.search.SearchService;
import com.gjl.music.playback.service.SubsonicService;
import com.gjl.music.playback.service.CoverArtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static java.util.concurrent.CompletableFuture.allOf;
import static java.util.concurrent.CompletableFuture.supplyAsync;

/**
 * Subsonic API 核心业务逻辑实现。
 *
 * <p>从 SubsonicController 提取所有业务方法，Controller 仅保留 HTTP 派发和响应序列化。
 */
@Slf4j
@Service
public class SubsonicServiceImpl implements SubsonicService {

    private final SongMapper songMapper;
    private final ArtistMapper artistMapper;
    private final AlbumMapper albumMapper;
    private final BrowseMapper browseMapper;
    private final LyricMapper lyricMapper;
    private final SearchService searchService;
    private final PlaylistService playlistService;
    private final CoverArtService coverArtService;
    private final PlayCountService playCountService;
    private final StreamingService streamingService;
    private final ConfigService configService;
    private final PlayCountMapper playCountMapper;
    private final IndexManager indexManager;
    private final SubsonicUserBridge userBridge;
    private final com.gjl.music.playback.service.TranscodeService transcodeService;
    private final com.gjl.music.playback.service.PlayQueueService playQueueService;

    public SubsonicServiceImpl(SongMapper songMapper,
                               ArtistMapper artistMapper,
                               AlbumMapper albumMapper,
                               BrowseMapper browseMapper,
                               LyricMapper lyricMapper,
                               SearchService searchService,
                               PlaylistService playlistService,
                               CoverArtService coverArtService,
                               PlayCountService playCountService,
                               PlayCountMapper playCountMapper,
                               IndexManager indexManager,
                               SubsonicUserBridge userBridge,
                               StreamingService streamingService,
                               ConfigService configService,
                               com.gjl.music.playback.service.TranscodeService transcodeService,
                               com.gjl.music.playback.service.PlayQueueService playQueueService) {
        this.songMapper = songMapper;
        this.artistMapper = artistMapper;
        this.albumMapper = albumMapper;
        this.browseMapper = browseMapper;
        this.lyricMapper = lyricMapper;
        this.searchService = searchService;
        this.playlistService = playlistService;
        this.coverArtService = coverArtService;
        this.playCountService = playCountService;
        this.playCountMapper = playCountMapper;
        this.indexManager = indexManager;
        this.userBridge = userBridge;
        this.streamingService = streamingService;
        this.configService = configService;
        this.transcodeService = transcodeService;
        this.playQueueService = playQueueService;
    }

    // ══════════════════════════════════════════════════
    // ThreadLocal 请求级缓存
    // ══════════════════════════════════════════════════

    /** 批量预加载风格 Map（请求级，用于消除 N+1） */
    private final ThreadLocal<Map<Long, String>> genrePreload = new ThreadLocal<>();

    /** 批量预加载播放计数（请求级） */
    private final ThreadLocal<Map<Long, Integer>> playCountPreload = new ThreadLocal<>();

    /** 批量预加载用户评分（请求级） */
    private final ThreadLocal<Map<Long, Integer>> ratingPreload = new ThreadLocal<>();

    /** 批量预加载多艺术家列表（请求级，OpenSubsonic artists 数组） */
    private final ThreadLocal<Map<Long, List<ArtistRef>>> artistListPreload = new ThreadLocal<>();

    /** 用户名 → userId 缓存（请求级） */
    private final ThreadLocal<Map<String, Long>> userIdCache =
            ThreadLocal.withInitial(HashMap::new);

    /** 按 ID 解析艺术家名（请求级缓存，同一请求内避免重复查 DB） */
    private final ThreadLocal<Map<Integer, String>> artistNameCache =
            ThreadLocal.withInitial(HashMap::new);

    /** 按 ID 解析专辑名（请求级缓存） */
    private final ThreadLocal<Map<Integer, String>> albumNameCache =
            ThreadLocal.withInitial(HashMap::new);

    /** 字母统计本地缓存（5 分钟 TTL，减少全表扫描） */
    private final Cache<String, List<Map<String, Object>>> songLettersCache =
            Caffeine.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).maximumSize(1).build();

    /** 清理当前请求所有 ThreadLocal 缓存（Controller 在 finally 中调用） */
    public void cleanupRequest() {
        userIdCache.remove();
        artistNameCache.remove();
        albumNameCache.remove();
        artistListPreload.remove();
    }

    // ══════════════════════════════════════════════════
    // System
    // ══════════════════════════════════════════════════

    public Map<String, Object> ping() {
        return Map.of();
    }

    public Map<String, Object> getLicense() {
        return Map.of("license", Map.of("valid", true));
    }

    public Map<String, Object> getNowPlaying() {
        List<Song> entries = playQueueService.getNowPlaying();
        List<Long> songIds = entries.stream()
                .map(s -> toLong(s.getId())).filter(Objects::nonNull).toList();
        Long userId = getUserIdAsLong();
        genrePreload.set(buildGenreMap(songIds));
        artistListPreload.set(batchResolveSongArtists(entries));
        if (userId != null && !songIds.isEmpty()) {
            playCountPreload.set(playCountService.getSongPlayCounts(userId, songIds));
            ratingPreload.set(playCountService.getUserRatings(userId, songIds));
        }
        try {
            return Map.of("nowPlaying", Map.of("entry",
                    entries.stream().map(this::toSongMap).toList()));
        } finally {
            genrePreload.remove(); playCountPreload.remove();
            ratingPreload.remove(); artistListPreload.remove();
        }
    }

    // ══════════════════════════════════════════════════
    // OpenSubsonic
    // ══════════════════════════════════════════════════

    public Map<String, Object> getOpenSubsonicExtensions() {
        List<Map<String, Object>> extensions = new ArrayList<>();
        extensions.add(Map.of("name", "songLyrics", "versions", List.of(1)));
        extensions.add(Map.of("name", "artists", "versions", List.of(1)));
        return Map.of("openSubsonicExtensions", extensions);
    }

    // ══════════════════════════════════════════════════
    // Browsing
    // ══════════════════════════════════════════════════

    public Map<String, Object> getMusicFolders() {
        return Map.of("musicFolders", Map.of("musicFolder", List.of(
                Map.of("id", "1", "name", "Music Library")
        )));
    }

    public Map<String, Object> getIndexes(SubsonicRequestParams p) {
        long ifModifiedSince = p.getInt("ifModifiedSince", 0);
        // ★ 一次性获取所有艺术家（修复 N+1 全表扫描），按首字母分组
        List<Artist> allArtists = browseMapper.findAllArtistsSimple();

        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (Artist a : allArtists) {
            if (a.getArtistName() == null || a.getArtistName().isEmpty()) continue;
            String firstLetter = a.getArtistName().substring(0, 1).toUpperCase();
            if (!firstLetter.matches("[A-Z]")) firstLetter = "#";
            grouped.computeIfAbsent(firstLetter, k -> new ArrayList<>())
                    .add(toArtistMap(a));
        }

        List<Map<String, Object>> indexEntries = new ArrayList<>();
        grouped.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> indexEntries.add(
                        Map.of("name", entry.getKey(), "artist", entry.getValue())));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("lastModified", System.currentTimeMillis() / 1000);
        result.put("ignoredArticles", "The El La Los Las Le Les");
        result.put("index", indexEntries);
        return Map.of("indexes", result);
    }

    public Map<String, Object> getArtists(SubsonicRequestParams p) {
        String letter = p.get("letter");
        String sort = p.get("sort");
        // 当客户端未指定 count 时，返回全部艺术家（标准 Subsonic 行为）
        // 当客户端指定 count 时，启用分页（最多 500）
        boolean hasCount = p.get("count") != null;
        int count = hasCount ? Math.min(p.getInt("count", 0), 500) : Integer.MAX_VALUE;
        int offset = hasCount ? p.getInt("offset", 0) : 0;
        List<Artist> artists = browseMapper.findArtists(letter, sort, offset, count);
        int total = browseMapper.countArtists(letter);
        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (Artist a : artists) {
            String firstLetter = a.getArtistName() != null && !a.getArtistName().isEmpty()
                    ? a.getArtistName().substring(0, 1).toUpperCase() : "#";
            if (!firstLetter.matches("[A-Z]")) firstLetter = "#";
            grouped.computeIfAbsent(firstLetter, k -> new ArrayList<>())
                    .add(toArtistMap(a));
        }
        List<Map<String, Object>> indexList = new ArrayList<>();
        for (var entry : grouped.entrySet()) {
            indexList.add(Map.of("name", entry.getKey(), "artist", entry.getValue()));
        }
        List<Map<String, Object>> letters = (letter == null)
                ? browseMapper.getArtistLetters() : List.of();
        return Map.of("artists", Map.of("index", indexList, "letters", letters, "total", total));
    }

    public Map<String, Object> getMusicDirectory(SubsonicRequestParams p) {
        String id = p.get("id");
        if (id == null) throw new IllegalArgumentException("缺少 id 参数");

        // ★ 双模式：纯数字 → 按专辑 ID 查询；非数字 → 按目录路径前缀查询
        try {
            Long albumId = Long.parseLong(id);
            List<Song> songs = songMapper.findSongsByAlbumId(albumId);
            Album album = albumMapper.findAlbumById(albumId);
            artistListPreload.set(batchResolveSongArtists(songs));
            try {
                return buildDirectory(id, album != null ? album.getAlbumName() : null,
                        songs.stream().map(this::toSongChildMap).toList());
            } finally {
                artistListPreload.remove();
            }
        } catch (NumberFormatException e) {
            // 非数字 ID → 文件路径前缀匹配（支持 getIndexes 返回的目录 ID 字符串）
            List<Song> songs = songMapper.findSongsByPathPrefix(id);
            String dirName = id.contains("/") ? id.substring(id.lastIndexOf('/') + 1) : id;
            artistListPreload.set(batchResolveSongArtists(songs));
            try {
                return buildDirectory(id, dirName,
                        songs.stream().map(this::toSongChildMap).toList());
            } finally {
                artistListPreload.remove();
            }
        }
    }

    public Map<String, Object> getArtist(SubsonicRequestParams p) {
        String id = p.get("id");
        if (id == null || id.isBlank()) return null;
        try {
            long artistId = Long.parseLong(id);
            Artist artist = artistMapper.findArtistById(artistId);
            if (artist == null) return null;
            Map<String, Object> artistMap = toArtistMap(artist);
            // Subsonic 规范：getArtist 需包含专辑列表
            List<Album> albums = browseMapper.findAlbumsByType(
                    "alphabetical", 0, 500, null, null, null, null, artistId);
            artistMap.put("album", albums.stream().map(this::toAlbumMap).toList());
            return Map.of("artist", artistMap);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public Map<String, Object> getArtistInfo(SubsonicRequestParams p) {
        return getArtistInfo2(p);
    }

    public Map<String, Object> getArtistInfo2(SubsonicRequestParams p) {
        String id = p.get("id");
        if (id == null || id.isBlank()) return null;
        try {
            long artistId = Long.parseLong(id);
            Artist artist = artistMapper.findArtistById(artistId);
            if (artist == null) return null;

            Map<String, Object> info = new LinkedHashMap<>();
            info.put("biography", artist.getIntroduction() != null ? artist.getIntroduction() : "");
            info.put("musicBrainzId", "");
            info.put("lastFmUrl", "");
            info.put("smallImageUrl", "");
            info.put("mediumImageUrl", "");
            info.put("largeImageUrl", "");
            // ★ 查找共享流派的相似艺术家（不再返回空列表）
            List<Artist> similar = browseMapper.findSimilarArtists(artistId, 5);
            info.put("similarArtist", similar.stream()
                    .map(this::toArtistMap).toList());

            return Map.of("artistInfo2", info);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Subsonic getAlbumInfo / getAlbumInfo2 — 专辑元信息 */
    public Map<String, Object> getAlbumInfo2(SubsonicRequestParams p) {
        String id = p.get("id");
        if (id == null || id.isBlank()) return null;
        try {
            long albumId = Long.parseLong(id);
            Album album = albumMapper.findAlbumById(albumId);
            if (album == null) return null;

            Map<String, Object> info = new LinkedHashMap<>();
            info.put("notes", album.getIntroduction() != null ? album.getIntroduction() : "");
            info.put("musicBrainzId", "");
            info.put("lastFmUrl", "");
            info.put("smallImageUrl", "");
            info.put("mediumImageUrl", "");
            info.put("largeImageUrl", "");

            return Map.of("albumInfo2", info);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Subsonic getLyrics — 原始歌词端点（通过 artist+title 搜索） */
    public Map<String, Object> getLyrics(SubsonicRequestParams p) {
        String artist = p.get("artist");
        String title = p.get("title");
        if (artist == null && title == null) {
            throw new IllegalArgumentException("需要 artist 或 title 参数");
        }

        String query = (artist != null ? artist : "") + " " + (title != null ? title : "");
        List<Long> songIds = searchService.searchSongs(query.trim(), 0, 5);
        if (songIds.isEmpty()) return null;

        for (Long songId : songIds) {
            Map<String, Object> lyricRow = lyricMapper.findLyricBySongId(songId);
            if (lyricRow != null && !lyricRow.isEmpty()) {
                String content = (String) lyricRow.get("CONTENT");
                if (content != null && !content.isBlank()) {
                    Map<String, Object> lyrics = new LinkedHashMap<>();
                    lyrics.put("artist", artist != null ? artist : "");
                    lyrics.put("title", title != null ? title : "");
                    lyrics.put("value", content);
                    return Map.of("lyrics", lyrics);
                }
            }
        }
        return null;
    }

    /** Subsonic search v1 — 向后兼容 pre-1.8.0 客户端 */
    public Map<String, Object> searchLegacy(SubsonicRequestParams p) {
        Map<String, Object> result = search(p);
        @SuppressWarnings("unchecked")
        Map<String, Object> searchResult3 = (Map<String, Object>) result.get("searchResult3");
        if (searchResult3 == null) return Map.of("searchResult", Map.of("match", List.of()));

        List<Map<String, Object>> matches = new ArrayList<>();
        addAllSafely(matches, searchResult3, "song");
        addAllSafely(matches, searchResult3, "album");
        addAllSafely(matches, searchResult3, "artist");

        return Map.of("searchResult", Map.of("match", matches));
    }

    @SuppressWarnings("unchecked")
    private static void addAllSafely(List<Map<String, Object>> dest, Map<String, Object> src, String key) {
        Object val = src.get(key);
        if (val instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map) dest.add((Map<String, Object>) item);
            }
        }
    }

    public Map<String, Object> getAlbum(SubsonicRequestParams p) {
        String id = p.get("id");
        if (id == null) return null;
        try {
            long albumId = Long.parseLong(id);
            Album album = albumMapper.findAlbumById(albumId);
            if (album == null) return null;
            Map<String, Object> albumMap = toAlbumMap(album);
            // Subsonic 规范：getAlbum 需包含歌曲列表
            List<Song> songs = songMapper.findSongsByAlbumId(albumId);
            albumMap.put("song", songs.stream().map(this::toSongMap).toList());
            // ★ 补齐顶层字段：genre + playCount
            String genre = songMapper.findFirstGenreByAlbumId(albumId);
            albumMap.put("genre", genre != null ? genre : "");
            albumMap.put("playCount", resolveAlbumPlayCount(albumId));
            return Map.of("album", albumMap);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public Map<String, Object> getSong(SubsonicRequestParams p) {
        String id = p.get("id");
        if (id == null) return null;
        try {
            Song song = songMapper.findSongById(Long.parseLong(id));
            if (song == null) return null;
            return Map.of("song", toSongMap(song));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public Map<String, Object> getGenres() {
        List<Map<String, Object>> dbGenres = browseMapper.findDistinctGenres();
        List<Map<String, Object>> genreList = new ArrayList<>();
        for (Map<String, Object> row : dbGenres) {
            Map<String, Object> g = new LinkedHashMap<>();
            g.put("value", getCaseInsensitive(row, "genre", "GENRE"));
            g.put("songCount", getCaseInsensitive(row, "songCount", "SONGCOUNT"));
            // ★ 新增 albumCount 字段（Subsonic 规范要求）
            g.put("albumCount", getCaseInsensitive(row, "albumCount", "ALBUMCOUNT"));
            if (row.containsKey("image") || row.containsKey("IMAGE")) {
                Object img = getCaseInsensitive(row, "image", "IMAGE");
                if (img != null) g.put("image", img);
            }
            genreList.add(g);
        }
        return Map.of("genres", Map.of("genre", genreList));
    }

    // ══════════════════════════════════════════════════
    // Album Lists
    // ══════════════════════════════════════════════════

    public Map<String, Object> getAlbumList(SubsonicRequestParams p) {
        String type = p.get("type", "newest");
        int size = Math.min(p.getInt("size", 10), 500);
        int offset = p.getInt("offset", 0);

        Long userId = getUserIdAsLong();
        List<Album> albums = browseMapper.findAlbumsByType(type, offset, size, userId, null, null, null, null);
        return Map.of("albumList", Map.of("album",
                albums.stream().map(this::toAlbumMap).toList()));
    }

    public Map<String, Object> getRandomSongs(SubsonicRequestParams p) {
        int size = Math.min(p.getInt("size", 10), 500);
        List<Song> songs = browseMapper.findRandomSongs(size);
        List<Long> songIds = songs.stream()
                .map(s -> toLong(s.getId())).filter(Objects::nonNull).toList();
        Long userId = getUserIdAsLong();
        genrePreload.set(buildGenreMap(songIds));
        artistListPreload.set(batchResolveSongArtists(songs));
        if (userId != null) {
            playCountPreload.set(playCountService.getSongPlayCounts(userId, songIds));
            ratingPreload.set(playCountService.getUserRatings(userId, songIds));
        }
        try {
            return Map.of("randomSongs", Map.of("song",
                    songs.stream().map(this::toSongMap).toList()));
        } finally {
            genrePreload.remove();
            playCountPreload.remove();
            ratingPreload.remove();
            artistListPreload.remove();
        }
    }

    public Map<String, Object> getSongsByGenre(SubsonicRequestParams p) {
        String genre = p.get("genre");
        if (genre == null) throw new IllegalArgumentException("缺少 genre 参数");
        int count = Math.min(p.getInt("count", 10), 500);
        int offset = p.getInt("offset", 0);
        Long userId = getUserIdAsLong();
        List<Song> songs = browseMapper.findSongsByGenre(genre, null, "alphabetical", offset, count, userId);
        List<Long> songIds = songs.stream()
                .map(s -> toLong(s.getId())).filter(Objects::nonNull).toList();
        genrePreload.set(buildGenreMap(songIds));
        artistListPreload.set(batchResolveSongArtists(songs));
        if (userId != null) {
            playCountPreload.set(playCountService.getSongPlayCounts(userId, songIds));
            ratingPreload.set(playCountService.getUserRatings(userId, songIds));
        }
        try {
            return Map.of("songsByGenre", Map.of("song",
                    songs.stream().map(this::toSongMap).toList()));
        } finally {
            genrePreload.remove();
            playCountPreload.remove();
            ratingPreload.remove();
            artistListPreload.remove();
        }
    }

    public Map<String, Object> getSimilarSongs(SubsonicRequestParams p) {
        String id = p.get("id");
        if (id == null) throw new IllegalArgumentException("缺少 id 参数");
        int count = Math.min(p.getInt("count", 50), 500);

        try {
            long songId = Long.parseLong(id);
            Song target = songMapper.findSongById(songId);
            if (target == null) return Map.of("similarSongs", Map.of("song", List.of()));

            // 策略：优先同艺术家歌曲，不足则补充同流派歌曲
            Set<Long> seenIds = new HashSet<>();
            seenIds.add(songId);
            List<Song> similar = new ArrayList<>();

            // 1) 同艺术家歌曲（通过 song_artist 关联）
            if (target.getArtistId() != null) {
                List<Song> artistSongs = songMapper.findSongsByArtistId(
                        target.getArtistId().longValue());
                for (Song s : artistSongs) {
                    Long sid = toLong(s.getId());
                    if (sid != null && seenIds.add(sid)) {
                        similar.add(s);
                        if (similar.size() >= count) break;
                    }
                }
            }

            // 2) 不足则补充同流派歌曲
            if (similar.size() < count) {
                String genre = songMapper.findFirstGenreBySongId(songId);
                if (genre != null && !genre.isBlank()) {
                    Long userId = getUserIdAsLong();
                    List<Song> genreSongs = browseMapper.findSongsByGenre(
                            genre, null, "random", 0, count * 2, userId);
                    for (Song s : genreSongs) {
                        Long sid = toLong(s.getId());
                        if (sid != null && seenIds.add(sid)) {
                            similar.add(s);
                            if (similar.size() >= count) break;
                        }
                    }
                }
            }

            // 3) 仍不足则补充随机歌曲
            if (similar.size() < count) {
                List<Song> randomSongs = browseMapper.findRandomSongs(count * 2);
                for (Song s : randomSongs) {
                    Long sid = toLong(s.getId());
                    if (sid != null && seenIds.add(sid)) {
                        similar.add(s);
                        if (similar.size() >= count) break;
                    }
                }
            }

            List<Long> songIds = similar.stream()
                    .map(s -> toLong(s.getId())).filter(Objects::nonNull).toList();
            Long userId = getUserIdAsLong();
            genrePreload.set(buildGenreMap(songIds));
            artistListPreload.set(batchResolveSongArtists(similar));
            if (userId != null) {
                playCountPreload.set(playCountService.getSongPlayCounts(userId, songIds));
                ratingPreload.set(playCountService.getUserRatings(userId, songIds));
            }
            try {
                return Map.of("similarSongs", Map.of("song",
                        similar.stream().map(this::toSongMap).toList()));
            } finally {
                genrePreload.remove();
                playCountPreload.remove();
                ratingPreload.remove();
                artistListPreload.remove();
            }
        } catch (NumberFormatException e) {
            return Map.of("similarSongs", Map.of("song", List.of()));
        }
    }

    public Map<String, Object> getTopSongs(SubsonicRequestParams p) {
        String artist = p.get("artist");
        if (artist == null || artist.isBlank()) throw new IllegalArgumentException("缺少 artist 参数");
        int count = Math.min(p.getInt("count", 50), 500);

        // ★ 按艺术家名查 DB（精确匹配），不经过 Lucene 文本搜索。
        // Lucene 分词器可能把 "221小伙伴" 切碎导致 0 结果，而按名查 ID 再查歌曲是确定性查询。
        List<Song> songs;
        Long artistId = artistMapper.selectArtistIdByName(artist);
        if (artistId != null) {
            songs = songMapper.findSongsByArtistId(artistId);
            if (songs.size() > count) songs = songs.subList(0, count);
            log.info("[Subsonic getTopSongs] artist='{}' → artistId={} → {} songs (limit={})",
                    artist, artistId, songs.size(), count);
        } else {
            // 精确匹配失败 → fallback Lucene 搜索（处理别名/拼写变体等边缘情况）
            log.info("[Subsonic getTopSongs] artist='{}' 精确匹配失败，fallback Lucene", artist);
            List<Long> songIds = searchService.searchSongs(artist, 0, count);
            songs = songIds.isEmpty() ? List.of() : songMapper.findSongsByIds(songIds);
        }

        Long userId = getUserIdAsLong();
        genrePreload.set(buildGenreMap(
                songs.stream().map(s -> toLong(s.getId())).filter(Objects::nonNull).toList()));
        artistListPreload.set(batchResolveSongArtists(songs));
        if (userId != null) {
            List<Long> ids = songs.stream().map(s -> toLong(s.getId())).filter(Objects::nonNull).toList();
            playCountPreload.set(playCountService.getSongPlayCounts(userId, ids));
            ratingPreload.set(playCountService.getUserRatings(userId, ids));
        }
        try {
            return Map.of("topSongs", Map.of("song",
                    songs.stream().map(this::toSongMap).toList()));
        } finally {
            genrePreload.remove();
            playCountPreload.remove();
            ratingPreload.remove();
            artistListPreload.remove();
        }
    }

    public Map<String, Object> getSongs(SubsonicRequestParams p) {
        String letter = p.get("letter");
        String sort = p.get("sort");
        int count = Math.min(p.getInt("count", 50), 500);
        int offset = p.getInt("offset", 0);
        Long userId = getUserIdAsLong();
        List<Song> songs = browseMapper.findSongsPaginated(offset, count, letter, sort, userId, null);
        int total = browseMapper.countSongs(letter, null);
        List<Long> songIds = songs.stream()
                .map(s -> toLong(s.getId())).filter(Objects::nonNull).toList();
        genrePreload.set(buildGenreMap(songIds));
        artistListPreload.set(batchResolveSongArtists(songs));
        if (userId != null) {
            playCountPreload.set(playCountService.getSongPlayCounts(userId, songIds));
            ratingPreload.set(playCountService.getUserRatings(userId, songIds));
        }
        try {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("song", songs.stream().map(this::toSongMap).toList());
            result.put("total", total);
            return Map.of("songs", result);
        } finally {
            genrePreload.remove();
            playCountPreload.remove();
            ratingPreload.remove();
            artistListPreload.remove();
        }
    }

    public Map<String, Object> getSongLetters() {
        List<Map<String, Object>> letters = songLettersCache.get("all",
                k -> browseMapper.getSongLetters());
        return Map.of("songLetters", letters);
    }

    // ══════════════════════════════════════════════════
    // Searching
    // ══════════════════════════════════════════════════

    public Map<String, Object> search(SubsonicRequestParams p) {
        String query = p.get("query");
        String effectiveQuery = (query == null || query.isBlank()) ? "" : query;

        int artistCount = Math.min(p.getInt("artistCount", 20), 100);
        int artistOffset = p.getInt("artistOffset", 0);
        int albumCount = Math.min(p.getInt("albumCount", 20), 100);
        int albumOffset = p.getInt("albumOffset", 0);
        int songCount = Math.min(p.getInt("songCount", 20), 100);
        int songOffset = p.getInt("songOffset", 0);

        // ★ 空查询 → 直接 DB 分页浏览，不走 Lucene
        //   Lucene 只用于内容搜索，浏览（空查询 = 列表全部歌曲/专辑/艺术家）走 DB 直查
        if (effectiveQuery.isEmpty()) {
            log.info("[SubsonicSearch] 空查询 → DB直查分页: songOffset={}, songCount={}, albumOffset={}, albumCount={}, artistOffset={}, artistCount={}",
                    songOffset, songCount, albumOffset, albumCount, artistOffset, artistCount);
            return searchDirectDb(effectiveQuery, songOffset, songCount,
                    albumOffset, albumCount, artistOffset, artistCount);
        }

        // ★ 关键字直通 DB：查询中包含配置关键字 → 跳过 Lucene，直接 SQL LIKE
        //   配置键 music.search.db-keywords（逗号分隔），如 "精确,exact"
        String dbKeywordsCsv = configService.getString("music.search.db-keywords", "");
        String matchedKeyword = findDbKeyword(effectiveQuery, dbKeywordsCsv);
        boolean skipLucene = matchedKeyword != null;

        if (skipLucene) {
            // 从查询中剥离关键字，避免干扰 SQL LIKE 匹配
            String cleanedQuery = effectiveQuery.replace(matchedKeyword, "").trim();
            log.info("[SubsonicSearch] 关键字命中直通DB: query='{}', keyword='{}', cleaned='{}'",
                    effectiveQuery, matchedKeyword, cleanedQuery);
            if (cleanedQuery.isEmpty()) {
                // 只有关键字没有搜索词 → 返回空结果
                return Map.of("searchResult3", Map.of(
                        "artist", List.of(), "album", List.of(), "song", List.of()));
            }
            return searchDirectDb(cleanedQuery, songOffset, songCount,
                    albumOffset, albumCount, artistOffset, artistCount);
        }

        // Phase 1: 并行 Lucene 搜索（3 路并发）
        CompletableFuture<List<Long>> songIdsFuture = supplyAsync(
                () -> searchService.searchSongs(effectiveQuery, songOffset, songCount));
        CompletableFuture<List<Long>> albumIdsFuture = supplyAsync(
                () -> searchService.searchAlbums(effectiveQuery, albumOffset, albumCount));
        CompletableFuture<List<Long>> artistIdsFuture = supplyAsync(
                () -> searchService.searchArtists(effectiveQuery, artistOffset, artistCount));
        allOf(songIdsFuture, albumIdsFuture, artistIdsFuture).join();

        List<Long> songIds = songIdsFuture.join();
        List<Long> albumIds = albumIdsFuture.join();
        List<Long> artistIds = artistIdsFuture.join();
        log.info("[SubsonicSearch] query={}, Lucene结果: songIds={}, albumIds={}, artistIds={}",
                effectiveQuery, songIds.size(), albumIds.size(), artistIds.size());

        // ★ DB 回退：Lucene 无结果时直接用 SQL LIKE 搜索（不要求索引为空，因为索引可能损坏/不全）
        if (songIds.isEmpty() && albumIds.isEmpty() && artistIds.isEmpty()) {
            log.info("[SubsonicSearch] Lucene 无结果，回退 DB LIKE 搜索: query='{}'", effectiveQuery);
            List<Song> songs = browseMapper.searchSongsByKeyword(effectiveQuery, songOffset, songCount);
            List<Album> albums = browseMapper.searchAlbumsByKeyword(effectiveQuery, albumOffset, albumCount);
            List<Artist> artists = browseMapper.searchArtistsByKeyword(effectiveQuery, artistOffset, artistCount);
            log.info("[SubsonicSearch] DB回退结果: songs={}, albums={}, artists={}",
                    songs.size(), albums.size(), artists.size());

            List<Long> fallbackSongIds = songs.stream()
                    .map(s -> toLong(s.getId())).filter(Objects::nonNull).toList();
            Long userId = getUserIdAsLong();
            genrePreload.set(buildGenreMap(fallbackSongIds));
            artistListPreload.set(batchResolveSongArtists(songs));
            if (userId != null && !fallbackSongIds.isEmpty()) {
                playCountPreload.set(playCountService.getSongPlayCounts(userId, fallbackSongIds));
                ratingPreload.set(playCountService.getUserRatings(userId, fallbackSongIds));
            }
            try {
                return Map.of("searchResult3", Map.of(
                        "artist", artists.stream().map(this::toArtistMap).toList(),
                        "album", albums.stream().map(this::toAlbumMap).toList(),
                        "song", songs.stream().map(this::toSongMap).toList()
                ));
            } finally {
                genrePreload.remove(); playCountPreload.remove();
                ratingPreload.remove(); artistListPreload.remove();
            }
        }

        // Phase 2: 并行 DB 水化（3 路并发）
        CompletableFuture<List<Song>> songsFuture = supplyAsync(
                () -> songIds.isEmpty() ? List.of() : songMapper.findSongsByIds(songIds));
        CompletableFuture<List<Album>> albumsFuture = supplyAsync(
                () -> albumIds.isEmpty() ? List.of() : albumMapper.findAlbumsByIds(albumIds));
        CompletableFuture<List<Artist>> artistsFuture = supplyAsync(
                () -> artistIds.isEmpty() ? List.of() : artistMapper.findArtistsByIds(artistIds));
        allOf(songsFuture, albumsFuture, artistsFuture).join();

        List<Song> songs = songsFuture.join();
        List<Album> albums = albumsFuture.join();
        List<Artist> artists = artistsFuture.join();
        log.info("[SubsonicSearch] DB水化结果: songs={}, albums={}, artists={}",
                songs.size(), albums.size(), artists.size());

        // Phase 3: 预加载 + 序列化（主线程，ThreadLocal 可用）
        Long userId = getUserIdAsLong();
        genrePreload.set(buildGenreMap(songIds));
        artistListPreload.set(batchResolveSongArtists(songs));
        if (userId != null) {
            playCountPreload.set(playCountService.getSongPlayCounts(userId, songIds));
            ratingPreload.set(playCountService.getUserRatings(userId, songIds));
        }
        try {
            return Map.of("searchResult3", Map.of(
                    "artist", artists.stream().map(this::toArtistMap).toList(),
                    "album", albums.stream().map(this::toAlbumMap).toList(),
                    "song", songs.stream().map(this::toSongMap).toList()
            ));
        } finally {
            genrePreload.remove();
            playCountPreload.remove();
            ratingPreload.remove();
            artistListPreload.remove();
        }
    }

    // ══════════════════════════════════════════════════
    // Playlists
    // ══════════════════════════════════════════════════

    public Map<String, Object> getPlaylists(SubsonicRequestParams p) {
        String username = getUsername();
        if (username == null) return Map.of("playlists", Map.of("playlist", List.of()));
        var pls = playlistService.listByUsername(username);

        final Map<Long, List<Song>> firstSongsMap;
        if (!pls.isEmpty()) {
            List<Long> allIds = pls.stream().map(Playlist::getId).toList();
            firstSongsMap = playlistService.getFirstSongsBatch(allIds, 4);
        } else {
            firstSongsMap = Map.of();
        }

        return Map.of("playlists", Map.of("playlist",
                pls.stream().map(pl -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", pl.getId());
                    m.put("name", pl.getName());
                    m.put("comment", pl.getComment() != null ? pl.getComment() : "");
                    m.put("owner", pl.getOwner());
                    m.put("public", pl.isPublic());
                    m.put("songCount", pl.getSongCount());
                    m.put("duration", (int) pl.getDuration());
                    m.put("created", pl.getCreatedAt() != null ? pl.getCreatedAt().toString() : "");
                    m.put("coverArt", "playlist-" + pl.getId());
                    List<Song> firstSongs = firstSongsMap.getOrDefault(pl.getId(), List.of());
                    m.put("entry", firstSongs.stream()
                            .map(s -> {
                                Map<String, Object> em = new LinkedHashMap<>();
                                em.put("id", s.getId());
                                em.put("coverArt", "song-" + s.getId());
                                return (Object) em;
                            })
                            .toList());
                    return m;
                }).toList()));
    }

    public Map<String, Object> getPlaylist(SubsonicRequestParams p) {
        String id = p.get("id");
        if (id == null) return null;
        Long plId = Long.parseLong(id);
        var pl = playlistService.getById(plId);
        if (pl == null) return null;
        List<Song> songs = playlistService.getSongs(plId);
        List<Long> songIds = songs.stream()
                .map(s -> toLong(s.getId())).filter(Objects::nonNull).toList();
        Long userId = getUserIdAsLong();
        genrePreload.set(buildGenreMap(songIds));
        artistListPreload.set(batchResolveSongArtists(songs));
        if (userId != null) {
            playCountPreload.set(playCountService.getSongPlayCounts(userId, songIds));
            ratingPreload.set(playCountService.getUserRatings(userId, songIds));
        }
        try {
            Map<String, Object> plMap = new LinkedHashMap<>();
            plMap.put("id", pl.getId());
            plMap.put("name", pl.getName());
            plMap.put("comment", pl.getComment() != null ? pl.getComment() : "");
            plMap.put("owner", pl.getOwner());
            plMap.put("public", pl.isPublic());
            plMap.put("songCount", pl.getSongCount());
            plMap.put("duration", (int) pl.getDuration());
            plMap.put("created", pl.getCreatedAt() != null ? pl.getCreatedAt().toString() : "");
            plMap.put("coverArt", "playlist-" + pl.getId());
            plMap.put("entry", songs.stream().map(this::toSongMap).toList());
            return Map.of("playlist", plMap);
        } finally {
            genrePreload.remove();
            playCountPreload.remove();
            ratingPreload.remove();
            artistListPreload.remove();
        }
    }

    public Map<String, Object> createPlaylist(SubsonicRequestParams p,
                                               HttpServletRequest request) {
        String name = p.get("name");
        if (name == null) {
            String plIdStr = p.get("playlistId");
            if (plIdStr == null) throw new IllegalArgumentException("缺少 name 或 playlistId");
            Long plId = Long.parseLong(plIdStr);
            String[] songIds = request.getParameterValues("songId");
            if (songIds != null) {
                List<Long> ids = Arrays.stream(songIds).map(Long::parseLong).toList();
                playlistService.addSongs(plId, ids);
            }
            // ★ 返回更新后的 playlist
            return buildPlaylistResponse(playlistService.getById(plId));
        }
        String username = getUsername();
        if (username == null) throw new IllegalArgumentException("未认证");
        Long userId = getUserIdAsLong();
        var pl = playlistService.createByUsername(name, "", username, userId, false);

        String[] songIds = request.getParameterValues("songId");
        if (songIds != null && songIds.length > 0) {
            List<Long> ids = Arrays.stream(songIds).map(Long::parseLong).toList();
            playlistService.addSongs(pl.getId(), ids);
        }
        // ★ 返回创建的 playlist
        return buildPlaylistResponse(playlistService.getById(pl.getId()));
    }

    public Map<String, Object> deletePlaylist(SubsonicRequestParams p) {
        String id = p.get("id");
        if (id == null) throw new IllegalArgumentException("缺少 id");
        playlistService.delete(Long.parseLong(id));
        return Map.of();
    }

    public Map<String, Object> updatePlaylist(SubsonicRequestParams p,
                                               HttpServletRequest request) {
        String idStr = p.get("playlistId");
        if (idStr == null) throw new IllegalArgumentException("缺少 playlistId");
        Long id = Long.parseLong(idStr);

        String[] toRemove = request.getParameterValues("songIndexToRemove");
        if (toRemove != null && toRemove.length > 0) {
            List<Integer> indices = Arrays.stream(toRemove)
                    .map(Integer::parseInt)
                    .sorted(Comparator.reverseOrder())
                    .toList();
            for (int idx : indices) {
                playlistService.removeSong(id, idx + 1);
            }
        }

        String[] toAdd = request.getParameterValues("songIdToAdd");
        if (toAdd != null && toAdd.length > 0) {
            List<Long> songIds = Arrays.stream(toAdd)
                    .map(Long::parseLong).toList();
            playlistService.addSongs(id, songIds);
        }

        String name = p.get("name");
        String comment = p.get("comment");
        String pub = p.get("public");
        if (name != null || comment != null || pub != null) {
            Playlist existing = playlistService.getById(id);
            if (existing != null) {
                playlistService.update(id,
                        name != null ? name : existing.getName(),
                        comment != null ? comment : existing.getComment(),
                        pub != null ? "true".equals(pub) : existing.isPublic(),
                        existing.getCoverPath());
            }
        }

        // ★ 返回更新后的 playlist
        return buildPlaylistResponse(playlistService.getById(id));
    }

    // ══════════════════════════════════════════════════
    // Media Retrieval
    // ══════════════════════════════════════════════════

    /** 1x1 透明 PNG 占位图（Subsonic 客户端期望始终收到图片，不应返回 404） */
    private static final byte[] PLACEHOLDER_PNG = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8/5+hHgAHggJ/PchI7w"
                    + "AAAABJRU5ErkJggg==");

    public void getCoverArt(SubsonicRequestParams p,
                            HttpServletResponse response) throws IOException {
        String id = p.get("id");
        if (id == null) throw new IllegalArgumentException("缺少 id");

        java.nio.file.Path coverPath = coverArtService.resolveCoverPath(id);
        if (coverPath != null && java.nio.file.Files.exists(coverPath)) {
            String mime = com.gjl.music.infra.util.ContentTypeResolver
                    .resolve(coverPath.getFileName().toString());
            response.setContentType(mime);
            java.nio.file.Files.copy(coverPath, response.getOutputStream());
        } else {
            // ★ 返回透明占位图而非 404（Subsonic 客户端期望始终收到图片）
            response.setContentType("image/png");
            response.setHeader("Cache-Control", "public, max-age=3600");
            response.getOutputStream().write(PLACEHOLDER_PNG);
        }
    }

    // ── stream / download ──

    /** Subsonic stream 端点 — 按 song ID 流式传输音频，支持转码参数 */
    public void stream(SubsonicRequestParams p,
                       HttpServletRequest request,
                       HttpServletResponse response) throws IOException {
        String id = p.get("id");
        if (id == null) throw new IllegalArgumentException("缺少 id");

        try {
            long songId = Long.parseLong(id);
            String format = p.get("format");
            int maxBitRate = p.getInt("maxBitRate", 0);
            int timeOffset = p.getInt("timeOffset", 0);
            String estimate = p.get("estimateContentLength");

            log.info("[SubsonicStream] id={}, format={}, maxBitRate={}, timeOffset={}, estimateContentLength={}",
                    id, format, maxBitRate, timeOffset, estimate);

            // 转码请求（format 非空且非 raw）或需要 maxBitRate 限制 → 使用 TranscodeService
            boolean needTranscode = (format != null && !format.isBlank() && !"raw".equalsIgnoreCase(format))
                    || maxBitRate > 0;
            if (needTranscode) {
                transcodeService.stream(songId, format, maxBitRate, timeOffset,
                        request, response, getUsername());
                return;
            }

            // 原始流：直接流式传输文件
            Song song = songMapper.findSongById(songId);
            if (song == null || song.getFilePath() == null) {
                log.warn("[SubsonicStream] song not found: id={}", id);
                response.setStatus(404);
                return;
            }
            log.info("[SubsonicStream] raw stream: id={}, title={}, filePath={}",
                    id, song.getTitle(), song.getFilePath());
            streamingService.streamFile(song.getFilePath(), request, response);
        } catch (NumberFormatException e) {
            log.warn("[SubsonicStream] invalid id: {}", id);
            response.setStatus(404);
        }
    }

    /** Subsonic download 端点 — 直接下载不做转码 */
    public void download(SubsonicRequestParams p,
                         HttpServletRequest request,
                         HttpServletResponse response) throws IOException {
        String id = p.get("id");
        if (id == null) throw new IllegalArgumentException("缺少 id");

        try {
            Song song = songMapper.findSongById(Long.parseLong(id));
            if (song == null || song.getFilePath() == null) {
                response.setStatus(404);
                return;
            }
            Path filePath = Paths.get(song.getFilePath());
            if (!Files.exists(filePath)) {
                response.setStatus(404);
                return;
            }
            String mime = com.gjl.music.infra.util.ContentTypeResolver
                    .resolve(filePath.getFileName().toString());
            response.setContentType(mime);
            response.setHeader("Content-Disposition",
                    "attachment; filename=\"" + filePath.getFileName().toString() + "\"");
            Files.copy(filePath, response.getOutputStream());
        } catch (NumberFormatException e) {
            response.setStatus(404);
        }
    }

    /** Subsonic getAvatar — 用户头像（从文件系统查找） */
    public void getAvatar(SubsonicRequestParams p,
                          HttpServletResponse response) throws IOException {
        String username = p.get("username");
        if (username == null) username = getUsername();
        if (username == null) {
            response.setStatus(404);
            return;
        }

        // 在 avatars 目录下查找用户头像
        String basePath = configService.getString("music.base-path", ".");
        Path avatarDir = Paths.get(basePath, "avatars");
        if (!Files.exists(avatarDir)) {
            response.setStatus(404);
            return;
        }

        // 查找匹配的 avatar 文件（支持 png/jpg/gif/webp）
        String[] exts = {".png", ".jpg", ".jpeg", ".gif", ".webp"};
        for (String ext : exts) {
            Path filePath = avatarDir.resolve(username + ext);
            if (Files.exists(filePath) && Files.isReadable(filePath)) {
                String mime = ext.equals(".png") ? "image/png"
                        : ext.equals(".gif") ? "image/gif"
                        : ext.equals(".webp") ? "image/webp"
                        : "image/jpeg";
                response.setContentType(mime);
                Files.copy(filePath, response.getOutputStream());
                return;
            }
            // 也检查按 userId 命名的文件
            Long userId = getUserIdAsLong();
            if (userId != null) {
                filePath = avatarDir.resolve(userId + ext);
                if (Files.exists(filePath) && Files.isReadable(filePath)) {
                    String mime = ext.equals(".png") ? "image/png"
                            : ext.equals(".gif") ? "image/gif"
                            : ext.equals(".webp") ? "image/webp"
                            : "image/jpeg";
                    response.setContentType(mime);
                    Files.copy(filePath, response.getOutputStream());
                    return;
                }
            }
        }
        response.setStatus(404);
    }

    // ── getUser ──

    /** Subsonic getUser — 返回当前认证用户信息 */
    public Map<String, Object> getUser(SubsonicRequestParams p) {
        String username = p.get("username");
        if (username == null) username = getUsername();
        if (username == null) return null;

        // 验证用户存在于 DB
        Long userId = browseMapper.findUserIdByUsername(username);
        if (userId == null) return null;

        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        boolean isAdmin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        Map<String, Object> userMap = new LinkedHashMap<>();
        userMap.put("username", username);
        userMap.put("email", "");
        userMap.put("scrobblingEnabled", true);
        userMap.put("adminRole", isAdmin);
        userMap.put("settingsRole", isAdmin);
        userMap.put("downloadRole", true);
        userMap.put("uploadRole", false);
        userMap.put("playlistRole", true);
        userMap.put("coverArtRole", true);
        userMap.put("commentRole", false);
        userMap.put("podcastRole", false);
        userMap.put("streamRole", true);
        userMap.put("jukeboxRole", false);
        userMap.put("shareRole", false);
        userMap.put("videoConversionRole", false);
        userMap.put("folder", List.of(1));
        return Map.of("user", userMap);
    }

    /** Subsonic getUsers — 返回所有用户列表（仅管理员） */
    public Map<String, Object> getUsers(SubsonicRequestParams p) {
        String username = getUsername();
        if (username == null) return null;
        // 仅管理员可调用
        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        boolean isAdmin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isAdmin) return null;

        try {
            List<Map<String, Object>> users = userBridge.getAllUsers();
            return Map.of("users", Map.of("user", users));
        } catch (Exception e) {
            log.error("getUsers 失败", e);
            return null;
        }
    }

    /** Subsonic changePassword — 管理员修改用户密码 */
    public Map<String, Object> changePassword(SubsonicRequestParams p) {
        String targetUser = p.get("username");
        String newPassword = p.get("password");
        if (targetUser == null || newPassword == null) {
            throw new IllegalArgumentException("缺少 username 或 password 参数");
        }

        String username = getUsername();
        if (username == null) throw new IllegalArgumentException("未认证");
        // 仅管理员可调用
        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        boolean isAdmin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isAdmin) throw new IllegalArgumentException("仅管理员可修改密码");

        userBridge.changePassword(targetUser, newPassword);
        return Map.of();
    }

    // ── Play Queue ──

    public Map<String, Object> getPlayQueue(SubsonicRequestParams p) {
        String username = getUsername();
        if (username == null) return Map.of("playQueue", Map.of("entry", List.of()));

        var queue = playQueueService.getQueueWithPosition(username);
        List<Song> songs = queue.songs();
        int currentPos = queue.currentPosition();

        List<Long> songIds = songs.stream()
                .map(s -> toLong(s.getId())).filter(Objects::nonNull).toList();
        Long userId = getUserIdAsLong();
        genrePreload.set(buildGenreMap(songIds));
        artistListPreload.set(batchResolveSongArtists(songs));
        if (userId != null) {
            playCountPreload.set(playCountService.getSongPlayCounts(userId, songIds));
            ratingPreload.set(playCountService.getUserRatings(userId, songIds));
        }
        try {
            Map<String, Object> playQueue = new LinkedHashMap<>();
            playQueue.put("entry", songs.stream().map(this::toSongMap).toList());
            playQueue.put("current", currentPos >= 0 && currentPos < songs.size()
                    ? String.valueOf(songs.get(currentPos).getId()) : "");
            playQueue.put("position", queue.positionMillis());
            playQueue.put("changed",
                    queue.changedAt() != null ? queue.changedAt().toString() : "");
            playQueue.put("changedBy",
                    queue.changedBy() != null ? queue.changedBy() : "");
            return Map.of("playQueue", playQueue);
        } finally {
            genrePreload.remove(); playCountPreload.remove();
            ratingPreload.remove(); artistListPreload.remove();
        }
    }

    public Map<String, Object> savePlayQueue(SubsonicRequestParams p,
                                              HttpServletRequest request) {
        String username = getUsername();
        if (username == null) return Map.of();
        String[] ids = request.getParameterValues("id");
        if (ids != null && ids.length > 0) {
            List<Long> songIds = Arrays.stream(ids).map(Long::parseLong).toList();
            playQueueService.saveQueue(username, songIds);
        }
        return Map.of();
    }

    // ── getScanStatus / startScan ──

    /** Subsonic getScanStatus — 返回扫描/索引状态 */
    public Map<String, Object> getScanStatus(SubsonicRequestParams p) {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("scanning", scanning);
        status.put("count", indexManager.getNumDocs() > 0
                ? (int) indexManager.getNumDocs() : browseMapper.countSongs(null, null));
        status.put("lastScan", lastScanTimestamp > 0
                ? java.time.Instant.ofEpochMilli(lastScanTimestamp).toString() : null);
        return Map.of("scanStatus", status);
    }

    /** Subsonic startScan — 触发全量 Lucene 索引重建（异步，立即返回） */
    public Map<String, Object> startScan(SubsonicRequestParams p) {
        if (scanning) {
            Map<String, Object> status = new LinkedHashMap<>();
            status.put("scanning", true);
            status.put("count", browseMapper.countSongs(null, null));
            return Map.of("scanStatus", status);
        }
        // 异步执行全量索引重建
        Thread.startVirtualThread(() -> {
            try {
                scanning = true;
                indexManager.buildFullIndex();
                lastScanTimestamp = System.currentTimeMillis();
            } catch (Exception e) {
                log.error("startScan 索引重建失败", e);
            } finally {
                scanning = false;
            }
        });
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("scanning", true);
        status.put("count", browseMapper.countSongs(null, null));
        return Map.of("scanStatus", status);
    }

    private volatile boolean scanning = false;
    private volatile long lastScanTimestamp = 0;

    // ══════════════════════════════════════════════════
    // Lyrics (OpenSubsonic)
    // ══════════════════════════════════════════════════

    public Map<String, Object> getLyricsBySongId(SubsonicRequestParams p) {
        String id = p.get("id");
        if (id == null) throw new IllegalArgumentException("缺少 id 参数");

        Song song = null;
        try {
            song = songMapper.findSongById(Long.parseLong(id));
        } catch (NumberFormatException ignored) {}
        if (song == null) return null;

        Map<String, Object> lyricRow = lyricMapper.findLyricBySongId(Long.parseLong(id));
        if (lyricRow == null || lyricRow.isEmpty()) return null;

        String content = (String) lyricRow.get("CONTENT");
        String type = (String) lyricRow.get("TYPE");
        if (content == null || content.isBlank()) return null;

        boolean isLrc = "LRC".equalsIgnoreCase(type) || looksLikeLrc(content);
        List<Map<String, Object>> lines = isLrc
                ? parseLrcLines(content)
                : splitPlainLines(content);

        Map<String, Object> structured = new LinkedHashMap<>();
        structured.put("displayArtist", resolveArtistName(song.getArtistId()));
        structured.put("displayTitle", song.getTitle() != null ? song.getTitle() : "");
        structured.put("lang", song.getLanguage() != null ? song.getLanguage() : "zho");
        structured.put("synced", isLrc);
        structured.put("line", lines);

        return Map.of("lyricsList", Map.of("structuredLyrics", List.of(structured)));
    }

    // ══════════════════════════════════════════════════
    // User Data — scrobble/star/rating
    // ══════════════════════════════════════════════════

    public Map<String, Object> scrobble(SubsonicRequestParams p) {
        String id = p.get("id");
        if (id == null) throw new IllegalArgumentException("缺少 id");
        if (!"true".equals(p.get("submission", "true"))) return Map.of();

        Long userId = getUserIdAsLong();
        if (userId == null) throw new IllegalArgumentException("未认证");

        try {
            Long songId = Long.parseLong(id);

            Song song = songMapper.findSongById(songId);
            if (song == null) return Map.of();
            Long albumId = song.getAlbumId() != null ? song.getAlbumId().longValue() : null;
            List<Long> artistIds = browseMapper.findArtistIdsBySongId(songId);
            List<Long> styleIds = browseMapper.findStyleIdsBySongId(songId);

            playCountService.scrobbleTransactional(userId, songId, albumId, artistIds, styleIds, "subsonic");
        } catch (NumberFormatException ignored) {}
        return Map.of();
    }

    public Map<String, Object> reportPlayback(SubsonicRequestParams p) {
        if (!"stopped".equals(p.get("state"))) return Map.of();
        String mediaId = p.get("mediaId");
        Long userId = getUserIdAsLong();
        if (mediaId == null || userId == null) return Map.of();

        try {
            Long songId = Long.parseLong(mediaId);

            Song song = songMapper.findSongById(songId);
            if (song == null || song.getDuration() == null) return Map.of();
            int posSec = p.getInt("positionMs", 0) / 1000;
            if (posSec < thresholdSeconds(song.getDuration())) return Map.of();

            Long albumId = song.getAlbumId() != null ? song.getAlbumId().longValue() : null;
            List<Long> artistIds = browseMapper.findArtistIdsBySongId(songId);
            List<Long> styleIds = browseMapper.findStyleIdsBySongId(songId);

            playCountService.scrobbleTransactional(userId, songId, albumId, artistIds, styleIds, "reportPlayback");
        } catch (NumberFormatException ignored) {}
        return Map.of();
    }

    public Map<String, Object> star(SubsonicRequestParams p) {
        Long userId = getUserIdAsLong();
        if (userId == null) throw new IllegalArgumentException("未认证");
        boolean isAlbum  = p.get("albumId") != null;
        boolean isArtist = p.get("artistId") != null;
        if (p.get("id") != null && !isAlbum && !isArtist)
            playCountService.star(userId, Long.parseLong(p.get("id")), "song");
        if (isAlbum)    playCountService.star(userId, Long.parseLong(p.get("albumId")), "album");
        if (isArtist)   playCountService.star(userId, Long.parseLong(p.get("artistId")), "artist");
        if (p.get("playlistId") != null) playCountService.star(userId, Long.parseLong(p.get("playlistId")), "playlist");
        return Map.of();
    }

    public Map<String, Object> unstar(SubsonicRequestParams p) {
        Long userId = getUserIdAsLong();
        if (userId == null) throw new IllegalArgumentException("未认证");
        boolean isAlbum  = p.get("albumId") != null;
        boolean isArtist = p.get("artistId") != null;
        if (p.get("id") != null && !isAlbum && !isArtist)
            playCountService.unstar(userId, Long.parseLong(p.get("id")), "song");
        if (isAlbum)    playCountService.unstar(userId, Long.parseLong(p.get("albumId")), "album");
        if (isArtist)   playCountService.unstar(userId, Long.parseLong(p.get("artistId")), "artist");
        if (p.get("playlistId") != null) playCountService.unstar(userId, Long.parseLong(p.get("playlistId")), "playlist");
        return Map.of();
    }

    public Map<String, Object> getStarred(SubsonicRequestParams p) {
        // getStarred 内容与 getStarred2 相同，但外层 wrapper 为 "starred"
        Map<String, Object> result = getStarred2(p);
        @SuppressWarnings("unchecked")
        Map<String, Object> starred2 = (Map<String, Object>) result.get("starred2");
        return Map.of("starred", starred2 != null ? starred2 : Map.of());
    }

    public Map<String, Object> getStarred2(SubsonicRequestParams p) {
        Long userId = getUserIdAsLong();
        if (userId == null) return Map.of("starred2", Map.of("song", List.of(), "album", List.of(), "artist", List.of(), "playlist", List.of()));

        List<Long> songIds = playCountService.getStarredIds(userId, "song");
        List<Song> songs = songIds.isEmpty() ? List.of() : songMapper.findSongsByIds(songIds);

        List<Long> albumIds = playCountService.getStarredIds(userId, "album");
        List<Album> starredAlbums = albumIds.isEmpty() ? List.of() : albumMapper.findAlbumsByIds(albumIds);

        List<Long> artistIds = playCountService.getStarredIds(userId, "artist");
        List<Artist> starredArtists = artistIds.isEmpty() ? List.of() : artistMapper.findArtistsByIds(artistIds);

        List<Long> playlistIds = playCountService.getStarredIds(userId, "playlist");
        List<Map<String, Object>> playlistMaps = List.of();
        if (!playlistIds.isEmpty()) {
            var pls = playlistService.listByUsername(getUsername());
            playlistMaps = pls.stream()
                    .filter(pl -> playlistIds.contains(pl.getId()))
                    .map(pl -> Map.<String, Object>of(
                            "id", pl.getId(),
                            "name", pl.getName(),
                            "comment", pl.getComment() != null ? pl.getComment() : "",
                            "owner", pl.getOwner(),
                            "public", pl.isPublic(),
                            "songCount", pl.getSongCount(),
                            "duration", (int) pl.getDuration(),
                            "created", pl.getCreatedAt() != null ? pl.getCreatedAt().toString() : ""
                    )).toList();
        }

        try {
            genrePreload.set(buildGenreMap(songIds));
            artistListPreload.set(batchResolveSongArtists(songs));
            if (userId != null) {
                playCountPreload.set(playCountService.getSongPlayCounts(userId, songIds));
                ratingPreload.set(playCountService.getUserRatings(userId, songIds));
            }

            return Map.of("starred2", Map.of(
                    "song", songs.stream().map(this::toSongMap).toList(),
                    "album", starredAlbums.stream().map(this::toAlbumMap).toList(),
                    "artist", starredArtists.stream().map(this::toArtistMap).toList(),
                    "playlist", playlistMaps));
        } finally {
            genrePreload.remove();
            playCountPreload.remove();
            ratingPreload.remove();
            artistListPreload.remove();
        }
    }

    public Map<String, Object> setRating(SubsonicRequestParams p) {
        Long userId = getUserIdAsLong();
        String id = p.get("id");
        int rating = p.getInt("rating", 0);
        if (userId == null || id == null) throw new IllegalArgumentException("缺少参数");
        if (rating < 1 || rating > 5) throw new IllegalArgumentException("评分范围 1-5");
        playCountService.rate(userId, Long.parseLong(id), "song", rating);
        return Map.of();
    }

    // ══════════════════════════════════════════════════
    // Playlist helper
    // ══════════════════════════════════════════════════

    private Map<String, Object> buildPlaylistResponse(Playlist pl) {
        if (pl == null) return Map.of();
        return Map.of("playlist", Map.of(
                "id", pl.getId(),
                "name", pl.getName(),
                "comment", pl.getComment() != null ? pl.getComment() : "",
                "owner", pl.getOwner(),
                "public", pl.isPublic(),
                "songCount", pl.getSongCount(),
                "duration", (int) pl.getDuration(),
                "created", pl.getCreatedAt() != null ? pl.getCreatedAt().toString() : "",
                "coverArt", "playlist-" + pl.getId()
        ));
    }

    // ══════════════════════════════════════════════════
    // Private helpers — 映射 / 缓存 / 工具
    // ══════════════════════════════════════════════════

    private String getUsername() {
        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }

    private Long getUserIdAsLong() {
        String username = getUsername();
        if (username == null) return null;
        Map<String, Long> cache = userIdCache.get();
        return cache.computeIfAbsent(username, u -> browseMapper.findUserIdByUsername(u));
    }

    private int resolvePlayCount(String songId) {
        try {
            Long id = Long.parseLong(songId);
            Map<Long, Integer> preload = playCountPreload.get();
            if (preload != null) return preload.getOrDefault(id, 0);
            Long userId = getUserIdAsLong();
            if (userId == null) return 0;
            return playCountService.getSongPlayCounts(userId, List.of(id)).getOrDefault(id, 0);
        } catch (NumberFormatException e) { return 0; }
    }

    private int resolveAlbumPlayCount(Long albumId) {
        if (albumId == null) return 0;
        Long userId = getUserIdAsLong();
        if (userId == null) return 0;
        Integer cnt = playCountMapper.getPlayCount(userId, albumId, "album");
        return cnt != null ? cnt : 0;
    }

    private int resolveUserRating(String songId) {
        try {
            Long id = Long.parseLong(songId);
            Map<Long, Integer> preload = ratingPreload.get();
            if (preload != null) return preload.getOrDefault(id, 0);
            Long userId = getUserIdAsLong();
            if (userId == null) return 0;
            return playCountService.getUserRatings(userId, List.of(id)).getOrDefault(id, 0);
        } catch (NumberFormatException e) { return 0; }
    }

    private Map<Long, String> buildGenreMap(List<Long> songIds) {
        if (songIds.isEmpty()) return Map.of();
        List<Map<String, Object>> rows = songMapper.findGenresBySongIds(songIds);
        Map<Long, String> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Object songIdObj = row.get("SONGID");
            if (songIdObj == null) songIdObj = row.get("songId");
            Object styleObj = row.get("STYLENAME");
            if (styleObj == null) styleObj = row.get("styleName");
            Long songId = toLong(songIdObj);
            if (songId != null && !result.containsKey(songId)) {
                result.put(songId, styleObj != null ? styleObj.toString() : "");
            }
        }
        return result;
    }

    /** 批量查询多艺术家（song_artist 全量），返回 songId → List&lt;ArtistRef&gt; */
    private Map<Long, List<ArtistRef>> batchResolveSongArtists(List<Song> songs) {
        if (songs == null || songs.isEmpty()) return Map.of();
        List<Long> songIds = songs.stream()
                .map(s -> toLong(s.getId()))
                .filter(Objects::nonNull)
                .distinct().toList();
        if (songIds.isEmpty()) return Map.of();
        return SongResult.groupArtistsBySongId(
                songMapper.findSongArtistsBySongIds(songIds));
    }

    private String resolveGenre(String songId) {
        try {
            if (songId == null || songId.isBlank()) return "";
            Map<Long, String> preload = genrePreload.get();
            Long id = Long.parseLong(songId);
            if (preload != null) return preload.getOrDefault(id, "");
            String genre = songMapper.findFirstGenreBySongId(id);
            return genre != null ? genre : "";
        } catch (NumberFormatException e) {
            return "";
        }
    }

    private String resolveArtistName(Integer artistId) {
        if (artistId == null) return "";
        Map<Integer, String> cache = artistNameCache.get();
        return cache.computeIfAbsent(artistId, id -> {
            Artist a = artistMapper.findArtistById(id.longValue());
            return a != null && a.getArtistName() != null ? a.getArtistName() : "";
        });
    }

    private String resolveAlbumName(Integer albumId) {
        if (albumId == null) return "";
        Map<Integer, String> cache = albumNameCache.get();
        return cache.computeIfAbsent(albumId, id -> {
            Album a = albumMapper.findAlbumById(id.longValue());
            return a != null && a.getAlbumName() != null ? a.getAlbumName() : "";
        });
    }

    private Map<String, Object> toSongMap(Song s) {
        // Build DTO with all resolved fields
        Integer artistId = s.getArtistId();
        if (artistId == null && s.getId() != null) {
            artistId = songMapper.findFirstArtistIdBySongId(Long.parseLong(s.getId()));
        }
        String resolvedArtist = artistId != null
                ? resolveArtistName(artistId)
                : (s.getArtistName() != null ? s.getArtistName() : "");
        String resolvedAlbum = s.getAlbumName() != null
                ? s.getAlbumName()
                : resolveAlbumName(s.getAlbumId());

        SongResult r = SongResult.fromSong(s).toBuilder()
                .artist(resolvedArtist)
                .album(resolvedAlbum)
                .genre(resolveGenre(s.getId()))
                .contentType(com.gjl.music.infra.util.FfmpegUtil.mimeTypeForFormat(s.getFileFormat()))
                .coverArt("song-" + s.getId())
                .build();

        Map<String, Object> m = r.toMap();
        // Subsonic-specific overrides / additions
        m.put("title", sanitize(s.getTitle()));
        m.put("parent", s.getAlbumId() != null ? String.valueOf(s.getAlbumId()) : "");
        m.put("albumId", s.getAlbumId() != null ? String.valueOf(s.getAlbumId()) : "");
        m.put("artistId", artistId != null ? String.valueOf(artistId) : "");
        m.put("year", parseYear(s.getYear()));
        m.put("track", s.getTrackNumber() != null ? s.getTrackNumber() : 0);
        m.put("discNumber", s.getDiscNumber() != null ? s.getDiscNumber() : 1);
        m.put("isDir", false);
        m.put("type", "music");
        m.put("isVideo", false);
        m.put("playCount", resolvePlayCount(s.getId()));
        m.put("userRating", resolveUserRating(s.getId()));

        // 确保 suffix 不为 null（Subsonic 客户端可能需要它来判断文件格式）
        if (m.get("suffix") == null || "".equals(m.get("suffix"))) {
            String suffix = s.getFileFormat();
            if (suffix == null || suffix.isBlank()) {
                // 从文件路径推断后缀
                String fp = s.getFilePath();
                if (fp != null && fp.contains(".")) {
                    suffix = fp.substring(fp.lastIndexOf('.') + 1).toLowerCase();
                }
            }
            if (suffix != null && !suffix.isBlank()) {
                m.put("suffix", suffix);
            }
        }

        // OpenSubsonic: 多艺术家扩展
        enrichArtistsToMap(m, s.getId());

        // 调试日志：关键播放字段
        log.info("[SubsonicSong] id={}, title={}, suffix={}, contentType={}, size={}, duration={}, isDir={}, type={}, path={}",
                s.getId(), s.getTitle(), m.get("suffix"), m.get("contentType"),
                m.get("size"), m.get("duration"), m.get("isDir"), m.get("type"), m.get("path"));

        return m;
    }

    /** 从 ThreadLocal 预加载中添加 artists / displayArtist 到响应 Map */
    private void enrichArtistsToMap(Map<String, Object> m, String songId) {
        if (songId == null) return;
        Map<Long, List<ArtistRef>> preload = artistListPreload.get();
        List<ArtistRef> artists;
        if (preload != null) {
            try { artists = preload.get(Long.parseLong(songId)); }
            catch (NumberFormatException e) { return; }
        } else {
            // 单曲查询回退：on-demand 查询
            List<Map<String, Object>> rows = songMapper.findSongArtistsBySongIds(List.of(Long.parseLong(songId)));
            artists = SongResult.groupArtistsBySongId(rows).get(Long.parseLong(songId));
        }
        if (artists != null && !artists.isEmpty()) {
            String joinSep = configService.getString("music.artist.join-separator", " & ");
            m.put("artists", Map.of("artist", artists.stream()
                    .filter(a -> !a.isEmpty())
                    .map(a -> Map.of("id", a.id(), "name", a.name()))
                    .toList()));
            m.put("displayArtist", artists.stream()
                    .map(ArtistRef::name)
                    .collect(Collectors.joining(joinSep)));
        }
    }

    private Map<String, Object> toAlbumMap(Album a) {
        AlbumResult r = AlbumResult.fromAlbum(a).toBuilder()
                .artist(resolveArtistName(a.getArtistId()))
                .coverArt("al-" + a.getId())
                .build();

        Map<String, Object> m = r.toMap();
        m.put("artistId", a.getArtistId() != null ? String.valueOf(a.getArtistId()) : "");
        m.put("year", a.getAlbumYear() != null ? a.getAlbumYear() : 0);
        m.put("songCount", a.getSongCount() != null ? a.getSongCount() : 0);
        return m;
    }

    private Map<String, Object> toArtistMap(Artist a) {
        ArtistResult r = ArtistResult.fromArtist(a).toBuilder()
                .coverArt("ar-" + a.getId())
                .build();

        Map<String, Object> m = r.toMap();
        m.put("name", a.getArtistName() != null ? a.getArtistName() : "");
        m.put("albumCount", a.getAlbumCount() != null ? a.getAlbumCount() : 0);
        m.put("songCount", a.getSongCount() != null ? a.getSongCount() : 0);
        return m;
    }

    private Map<String, Object> toSongChildMap(Song s) {
        Map<String, Object> m = toSongMap(s);
        m.put("isDir", false);
        m.put("type", "music");
        m.put("parent", s.getAlbumId() != null ? String.valueOf(s.getAlbumId()) : "");
        return m;
    }

    private Map<String, Object> buildDirectory(String id, String name, List<Map<String, Object>> children) {
        Map<String, Object> dir = new LinkedHashMap<>();
        dir.put("id", id);
        dir.put("name", name != null ? name : id);
        dir.put("child", children);
        return Map.of("directory", dir);
    }

    // ══════════════════════════════════════════════════
    // Search helpers
    // ══════════════════════════════════════════════════

    /** 检查查询是否包含配置的 DB 直通关键字，返回匹配到的关键字（null=未匹配） */
    private static String findDbKeyword(String query, String dbKeywordsCsv) {
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

    /** 直接DB搜索（SQL LIKE），跳过 Lucene */
    private Map<String, Object> searchDirectDb(String query,
                                                int songOffset, int songCount,
                                                int albumOffset, int albumCount,
                                                int artistOffset, int artistCount) {
        List<Song> songs = browseMapper.searchSongsByKeyword(query, songOffset, songCount);
        List<Album> albums = browseMapper.searchAlbumsByKeyword(query, albumOffset, albumCount);
        List<Artist> artists = browseMapper.searchArtistsByKeyword(query, artistOffset, artistCount);
        log.info("[SubsonicSearch] DB直通结果: query='{}', songs={}, albums={}, artists={}",
                query, songs.size(), albums.size(), artists.size());

        List<Long> songIds = songs.stream()
                .map(s -> toLong(s.getId())).filter(Objects::nonNull).toList();
        Long userId = getUserIdAsLong();
        genrePreload.set(buildGenreMap(songIds));
        artistListPreload.set(batchResolveSongArtists(songs));
        if (userId != null && !songIds.isEmpty()) {
            playCountPreload.set(playCountService.getSongPlayCounts(userId, songIds));
            ratingPreload.set(playCountService.getUserRatings(userId, songIds));
        }
        try {
            return Map.of("searchResult3", Map.of(
                    "artist", artists.stream().map(this::toArtistMap).toList(),
                    "album", albums.stream().map(this::toAlbumMap).toList(),
                    "song", songs.stream().map(this::toSongMap).toList()
            ));
        } finally {
            genrePreload.remove(); playCountPreload.remove();
            ratingPreload.remove(); artistListPreload.remove();
        }
    }

    // ══════════════════════════════════════════════════
    // Static utilities
    // ══════════════════════════════════════════════════

    private static String sanitize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.indexOf('\0') >= 0 ? s.replace("\0", "") : s;
    }

    private static int parseYear(String raw) {
        if (raw == null || raw.isBlank()) return 0;
        String s = sanitize(raw).trim();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d{4}").matcher(s);
        if (m.find()) {
            try { return Integer.parseInt(m.group()); }
            catch (NumberFormatException ignored) {}
        }
        return 0;
    }

    private static Object getCaseInsensitive(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            if (map.containsKey(key)) return map.get(key);
        }
        return null;
    }

    private static Long toLong(Object obj) {
        if (obj instanceof Number n) return n.longValue();
        if (obj instanceof String s) {
            try { return Long.parseLong(s); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    private static int thresholdSeconds(int totalSec) {
        if (totalSec < 60) return (int) (totalSec * 0.8);
        if (totalSec < 480) return Math.min((int) (totalSec * 0.5), 120);
        return 240;
    }

    private static List<Map<String, Object>> parseLrcLines(String lrc) {
        List<Map<String, Object>> lines = new ArrayList<>();
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "\\[(\\d{2}):(\\d{2})(?:\\.(\\d{2,3}))?\\](.*)");
        for (String raw : lrc.split("\\n")) {
            String line = raw.strip();
            if (line.isEmpty()) continue;
            java.util.regex.Matcher m = pattern.matcher(line);
            if (m.matches()) {
                int min = Integer.parseInt(m.group(1));
                int sec = Integer.parseInt(m.group(2));
                String msStr = m.group(3);
                int ms = msStr != null
                        ? Integer.parseInt(msStr.length() == 2 ? msStr + "0" : msStr)
                        : 0;
                long startMs = (min * 60L + sec) * 1000 + ms;
                String text = m.group(4).strip();
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("start", startMs);
                entry.put("value", text.isEmpty() ? "♪" : text);
                lines.add(entry);
            }
        }
        return lines;
    }

    private static boolean looksLikeLrc(String text) {
        if (text == null || text.isBlank()) return false;
        String firstLine = text.strip().lines().findFirst().orElse("").strip();
        return firstLine.matches("\\[\\d{2}:\\d{2}[.:]\\d{2,3}\\].*");
    }

    private static List<Map<String, Object>> splitPlainLines(String text) {
        List<Map<String, Object>> lines = new ArrayList<>();
        for (String raw : text.split("\\n")) {
            String line = raw.strip();
            if (line.isEmpty()) continue;
            lines.add(Map.of("value", line));
        }
        return lines;
    }
}
