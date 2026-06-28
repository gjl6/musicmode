package com.gjl.music.playback.service.impl;

import com.gjl.music.config.ConfigService;
import com.gjl.music.dto.ArtistRef;
import com.gjl.music.dto.SongResult;
import com.gjl.music.mapper.SongMapper;
import com.gjl.music.mapper.StyleMapper;
import com.gjl.music.model.Song;
import com.gjl.music.playback.mapper.BrowseMapper;
import com.gjl.music.playback.service.GenreService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class GenreServiceImpl implements GenreService {

    private final BrowseMapper browseMapper;
    private final StyleMapper styleMapper;
    private final SongMapper songMapper;
    private final ConfigService configService;

    public GenreServiceImpl(BrowseMapper browseMapper,
                            StyleMapper styleMapper,
                            SongMapper songMapper,
                            ConfigService configService) {
        this.browseMapper = browseMapper;
        this.styleMapper = styleMapper;
        this.songMapper = songMapper;
        this.configService = configService;
    }

    public Map<String, Object> getGenres(String sort, String letter, int limit, int offset) {
        limit = Math.min(limit, 500);
        List<Map<String, Object>> genres = browseMapper.findGenresPaginated(letter, sort, offset, limit);
        int total = browseMapper.countGenres(letter);

        for (Map<String, Object> g : genres) {
            Object id = g.get("id");
            if (id != null) {
                g.put("coverArt", "genre-" + id);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("genres", genres);
        result.put("total", total);
        return result;
    }

    public Map<String, Object> getGenreLetters() {
        List<Map<String, Object>> letters = browseMapper.getGenreLetters();
        return Map.of("letters", letters);
    }

    public Map<String, Object> getGenreSongs(String name, String letter, String sort,
                                              int limit, int offset, Long userId) {
        limit = Math.min(limit, 500);
        if ("frequent".equals(sort) && userId == null) {
            sort = "alphabetical";
        }
        List<Song> songs = browseMapper.findSongsByGenre(name, letter, sort, offset, limit, userId);
        int total = browseMapper.countSongsByGenre(name, letter);
        Map<String, Object> genreInfo = styleMapper.selectStyleInfoByName(name);

        if (genreInfo != null && genreInfo.get("id") != null) {
            genreInfo.put("coverArt", "genre-" + genreInfo.get("id"));
        }

        Map<Long, List<ArtistRef>> artistMap = batchResolveArtists(songs);
        String joinSep = getArtistJoinSeparator();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("genre", genreInfo != null ? genreInfo : Map.of("name", name));
        result.put("songs", songs.stream()
                .map(s -> SongResult.enrichArtists(toSongResult(s), artistMap, joinSep))
                .toList());
        result.put("total", total);
        return result;
    }

    public Long resolveUserId(String username) {
        if (username == null) return null;
        return browseMapper.findUserIdByUsername(username);
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
