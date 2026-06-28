package com.gjl.music.playback.service.impl;

import com.gjl.music.config.ConfigService;
import com.gjl.music.dto.AlbumResult;
import com.gjl.music.dto.ArtistRef;
import com.gjl.music.dto.SongResult;
import com.gjl.music.mapper.AlbumMapper;
import com.gjl.music.mapper.ArtistMapper;
import com.gjl.music.mapper.SongMapper;
import com.gjl.music.model.Album;
import com.gjl.music.model.Artist;
import com.gjl.music.model.Song;
import com.gjl.music.playback.mapper.BrowseMapper;
import com.gjl.music.playback.service.AlbumService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class AlbumServiceImpl implements AlbumService {

    private final BrowseMapper browseMapper;
    private final AlbumMapper albumMapper;
    private final SongMapper songMapper;
    private final ArtistMapper artistMapper;

    /** 字母统计缓存（5 分钟 TTL） */
    private final Cache<String, List<Map<String, Object>>> albumLettersCache =
            Caffeine.newBuilder()
                    .expireAfterWrite(5, TimeUnit.MINUTES)
                    .maximumSize(1)
                    .build();

    /** 艺术家名称缓存（10 分钟 TTL） */
    private final Cache<Integer, String> artistNameCache =
            Caffeine.newBuilder()
                    .expireAfterWrite(10, TimeUnit.MINUTES)
                    .maximumSize(5000)
                    .build();

    private final ConfigService configService;

    public AlbumServiceImpl(BrowseMapper browseMapper,
                            AlbumMapper albumMapper,
                            SongMapper songMapper,
                            ArtistMapper artistMapper,
                            ConfigService configService) {
        this.browseMapper = browseMapper;
        this.albumMapper = albumMapper;
        this.songMapper = songMapper;
        this.artistMapper = artistMapper;
        this.configService = configService;
    }

    public Map<String, Object> getAlbums(String sort, String letter, Boolean starred,
                                          int limit, int offset, String username) {
        limit = Math.min(limit, 500);
        List<Album> albums = browseMapper.findAlbumsByType(sort, offset, limit, null,
                letter, starred, username, null);
        int total = browseMapper.countAlbumsByLetter(letter, starred, username, null);

        List<AlbumResult> albumResults = new ArrayList<>();
        for (Album a : albums) {
            albumResults.add(toAlbumResult(a));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("albums", albumResults);
        result.put("total", total);
        return result;
    }

    public Map<String, Object> getAlbumLetters(Boolean starred, String username) {
        String cacheKey = username != null ? "starred-" + username : "all";
        List<Map<String, Object>> letters = albumLettersCache.get(cacheKey,
                k -> browseMapper.getAlbumLetters(starred, username));
        return Map.of("letters", letters);
    }

    public Map<String, Object> getAlbum(Long id) {
        Album album = albumMapper.findAlbumById(id);
        if (album == null) return null;
        List<Song> songs = songMapper.findSongsByAlbumId(id);
        // 批量查询所有艺术家
        Map<Long, List<ArtistRef>> artistMap = batchResolveArtists(songs);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("album", toAlbumResult(album));
        String joinSep = getArtistJoinSeparator();
        result.put("songs", songs.stream()
                .map(s -> SongResult.enrichArtists(toSongResult(s), artistMap, joinSep))
                .toList());
        return result;
    }

    public Map<String, Object> getAlbumSongs(Long id) {
        List<Song> songs = songMapper.findSongsByAlbumId(id);
        Map<Long, List<ArtistRef>> artistMap = batchResolveArtists(songs);
        String joinSep = getArtistJoinSeparator();
        return Map.of("songs", songs.stream()
                .map(s -> SongResult.enrichArtists(toSongResult(s), artistMap, joinSep))
                .toList());
    }

    // ── DTO 映射 ──

    public AlbumResult toAlbumResult(Album a) {
        return AlbumResult.fromAlbum(a).toBuilder()
                .artist(resolveArtistName(a.getArtistId()))
                .coverArt("album-" + a.getId())
                .genre(a.getAlbumType() != null ? a.getAlbumType().getDisplayName() : null)
                .build();
    }

    public String resolveArtistName(Integer artistId) {
        if (artistId == null) return "";
        return artistNameCache.get(artistId, id -> {
            Artist artist = artistMapper.findArtistById(id.longValue());
            return artist != null && artist.getArtistName() != null
                    ? artist.getArtistName() : "";
        });
    }

    public SongResult toSongResult(Song s) {
        return SongResult.fromSong(s).toBuilder()
                .coverArt("song-" + s.getId())
                .build();
    }

    // ── 批量艺术家 ──

    private String getArtistJoinSeparator() {
        return configService.getString("music.artist.join-separator", " & ");
    }

    private Map<Long, List<ArtistRef>> batchResolveArtists(List<Song> songs) {
        if (songs == null || songs.isEmpty()) return Map.of();
        List<Long> songIds = songs.stream()
                .map(s -> Long.parseLong(s.getId()))
                .distinct().toList();
        return SongResult.groupArtistsBySongId(
                songMapper.findSongArtistsBySongIds(songIds));
    }
}
