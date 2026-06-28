package com.gjl.music.playback.service.impl;

import com.gjl.music.config.ConfigService;
import com.gjl.music.dto.AlbumResult;
import com.gjl.music.dto.ArtistRef;
import com.gjl.music.dto.ArtistResult;
import com.gjl.music.dto.SongResult;
import com.gjl.music.mapper.ArtistMapper;
import com.gjl.music.mapper.SongMapper;
import com.gjl.music.model.Album;
import com.gjl.music.model.Artist;
import com.gjl.music.model.Song;
import com.gjl.music.playback.mapper.BrowseMapper;
import com.gjl.music.playback.mapper.PlayCountMapper;
import com.gjl.music.playback.service.ArtistService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ArtistServiceImpl implements ArtistService {

    private final ArtistMapper artistMapper;
    private final BrowseMapper browseMapper;
    private final SongMapper songMapper;
    private final PlayCountMapper playCountMapper;
    private final ConfigService configService;

    public ArtistServiceImpl(ArtistMapper artistMapper,
                             BrowseMapper browseMapper,
                             SongMapper songMapper,
                             PlayCountMapper playCountMapper,
                             ConfigService configService) {
        this.artistMapper = artistMapper;
        this.browseMapper = browseMapper;
        this.songMapper = songMapper;
        this.playCountMapper = playCountMapper;
        this.configService = configService;
    }

    public Map<String, Object> getArtist(Long id) {
        Artist artist = artistMapper.findArtistById(id);
        if (artist == null) return null;
        return Map.of("artist", toArtistResult(artist));
    }

    public Map<String, Object> getTopArtists(Long userId, int limit) {
        limit = Math.min(limit, 100);
        List<Map<String, Object>> rows = playCountMapper.getTopArtistIds(userId, limit);
        if (rows.isEmpty()) return Map.of("artists", List.of(), "total", 0);

        List<Long> artistIds = rows.stream()
                .map(r -> ((Number) r.get("artistId")).longValue())
                .toList();
        List<Artist> artists = artistMapper.findArtistsByIds(artistIds);

        // 按播放量排序（保持与聚合查询一致的顺序）
        Map<Long, Integer> rankMap = rows.stream()
                .collect(Collectors.toMap(
                        r -> ((Number) r.get("artistId")).longValue(),
                        r -> ((Number) r.get("totalPlays")).intValue()));
        Comparator<Artist> byPlayCount = Comparator.comparingInt(
                a -> rankMap.getOrDefault(Long.parseLong(a.getId()), 0));
        List<ArtistResult> sorted = artists.stream()
                .sorted(byPlayCount.reversed())
                .map(this::toArtistResult)
                .toList();

        return Map.of("artists", sorted, "total", sorted.size());
    }

    public Map<String, Object> getArtistAlbums(Long id, String sort, String letter,
                                                int limit, int offset, Long userId) {
        limit = Math.min(limit, 500);
        List<Album> albums = browseMapper.findAlbumsByType(
                sort, offset, limit, userId, letter, null, null, id);
        int total = browseMapper.countAlbumsByLetter(letter, null, null, id);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("albums", albums.stream().map(this::toAlbumResult).collect(Collectors.toList()));
        result.put("total", total);
        return result;
    }

    public Map<String, Object> getArtistSongs(Long id, String letter, String sort,
                                               int limit, int offset, Long userId) {
        limit = Math.min(limit, 500);
        List<Song> songs = browseMapper.findSongsPaginated(
                offset, limit, letter, sort, userId, id);
        int total = browseMapper.countSongs(letter, id);

        Map<Long, List<ArtistRef>> artistMap = batchResolveArtists(songs);
        String joinSep = getArtistJoinSeparator();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("songs", songs.stream()
                .map(s -> SongResult.enrichArtists(toSongResult(s), artistMap, joinSep))
                .collect(Collectors.toList()));
        result.put("total", total);
        return result;
    }

    public Long resolveUserId(String username) {
        if (username == null) return null;
        return browseMapper.findUserIdByUsername(username);
    }

    // ── DTO 映射（public 供 SubsonicService 复用）──

    public ArtistResult toArtistResult(Artist a) {
        return ArtistResult.fromArtist(a).toBuilder()
                .coverArt("artist-" + a.getId())
                .build();
    }

    public AlbumResult toAlbumResult(Album a) {
        return AlbumResult.fromAlbum(a).toBuilder()
                .coverArt("album-" + a.getId())
                .genre(a.getAlbumType() != null ? a.getAlbumType().getDisplayName() : null)
                .build();
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
