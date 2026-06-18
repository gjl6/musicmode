package com.gjl.music.playback.service.impl;

import com.gjl.music.mapper.MusicMapper;
import com.gjl.music.model.Song;
import com.gjl.music.playback.service.GenreService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class GenreServiceImpl implements GenreService {

    private final MusicMapper musicMapper;

    public GenreServiceImpl(MusicMapper musicMapper) {
        this.musicMapper = musicMapper;
    }

    public Map<String, Object> getGenres(String sort, String letter, int limit, int offset) {
        limit = Math.min(limit, 500);
        List<Map<String, Object>> genres = musicMapper.findGenresPaginated(letter, sort, offset, limit);
        int total = musicMapper.countGenres(letter);

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
        List<Map<String, Object>> letters = musicMapper.getGenreLetters();
        return Map.of("letters", letters);
    }

    public Map<String, Object> getGenreSongs(String name, String letter, String sort,
                                              int limit, int offset, Long userId) {
        limit = Math.min(limit, 500);
        if ("frequent".equals(sort) && userId == null) {
            sort = "alphabetical";
        }
        List<Song> songs = musicMapper.findSongsByGenre(name, letter, sort, offset, limit, userId);
        int total = musicMapper.countSongsByGenre(name, letter);
        Map<String, Object> genreInfo = musicMapper.selectStyleInfoByName(name);

        if (genreInfo != null && genreInfo.get("id") != null) {
            genreInfo.put("coverArt", "genre-" + genreInfo.get("id"));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("genre", genreInfo != null ? genreInfo : Map.of("name", name));
        result.put("songs", songs.stream().map(this::toSongMap).toList());
        result.put("total", total);
        return result;
    }

    public Long resolveUserId(String username) {
        if (username == null) return null;
        return musicMapper.findUserIdByUsername(username);
    }

    public Map<String, Object> toSongMap(Song s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("title", s.getTitle());
        m.put("artist", s.getArtistName());
        m.put("album", s.getAlbumName());
        m.put("albumId", s.getAlbumId() != null ? String.valueOf(s.getAlbumId()) : null);
        m.put("track", s.getTrackNumber());
        m.put("discNumber", s.getDiscNumber());
        m.put("duration", s.getDuration());
        m.put("year", s.getYear());
        m.put("path", s.getFilePath());
        m.put("suffix", s.getFileFormat());
        m.put("bitRate", s.getBitrate());
        m.put("size", s.getFileSize());
        m.put("coverArt", "song-" + s.getId());
        return m;
    }
}
