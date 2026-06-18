package com.gjl.music.playback.service.impl;

import com.gjl.music.mapper.MusicMapper;
import com.gjl.music.model.Album;
import com.gjl.music.model.AlbumType;
import com.gjl.music.model.Artist;
import com.gjl.music.model.Song;
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

    private final MusicMapper musicMapper;


    private final Cache<String, List<Map<String, Object>>> albumLettersCache =
            Caffeine.newBuilder()
                    .expireAfterWrite(5, TimeUnit.MINUTES)
                    .maximumSize(1)
                    .build();


    private final Cache<Integer, String> artistNameCache =
            Caffeine.newBuilder()
                    .expireAfterWrite(10, TimeUnit.MINUTES)
                    .maximumSize(5000)
                    .build();

    public AlbumServiceImpl(MusicMapper musicMapper) {
        this.musicMapper = musicMapper;
    }

    public Map<String, Object> getAlbums(String sort, String letter, Boolean starred,
                                          int limit, int offset, String username) {
        limit = Math.min(limit, 500);
        List<Album> albums = musicMapper.findAlbumsByType(sort, offset, limit, null,
                letter, starred, username, null);
        int total = musicMapper.countAlbumsByLetter(letter, starred, username, null);

        List<Map<String, Object>> albumMaps = new ArrayList<>();
        for (Album a : albums) {
            albumMaps.add(toAlbumMap(a));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("albums", albumMaps);
        result.put("total", total);
        return result;
    }

    public Map<String, Object> getAlbumLetters(Boolean starred, String username) {
        String cacheKey = username != null ? "starred-" + username : "all";
        List<Map<String, Object>> letters = albumLettersCache.get(cacheKey,
                k -> musicMapper.getAlbumLetters(starred, username));
        return Map.of("letters", letters);
    }

    public Map<String, Object> getAlbum(Long id) {
        Album album = musicMapper.findAlbumById(id);
        if (album == null) return null;
        List<Song> songs = musicMapper.findSongsByAlbumId(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("album", toAlbumMap(album));
        result.put("songs", songs.stream().map(this::toSongMap).toList());
        return result;
    }

    public Map<String, Object> getAlbumSongs(Long id) {
        List<Song> songs = musicMapper.findSongsByAlbumId(id);
        return Map.of("songs", songs.stream().map(this::toSongMap).toList());
    }


    public Map<String, Object> toAlbumMap(Album a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("name", a.getAlbumName() != null ? a.getAlbumName() : "");
        m.put("artist", resolveArtistName(a.getArtistId()));
        m.put("artistId", a.getArtistId() != null ? String.valueOf(a.getArtistId()) : null);
        m.put("coverArt", "album-" + a.getId());
        m.put("songCount", a.getSongCount() != null ? a.getSongCount() : 0);
        m.put("year", a.getAlbumYear() != null ? a.getAlbumYear() : 0);
        m.put("genre", a.getAlbumType() != null ? a.getAlbumType().getDisplayName() : null);
        m.put("albumType", a.getAlbumType() != null ? a.getAlbumType().name() : null);
        m.put("introduction", a.getIntroduction());
        m.put("company", a.getCompany());
        m.put("language", a.getLanguage());
        m.put("created", a.getCreateTime());
        return m;
    }

    public String resolveArtistName(Integer artistId) {
        if (artistId == null) return "";
        return artistNameCache.get(artistId, id -> {
            Artist artist = musicMapper.findArtistById(id.longValue());
            return artist != null && artist.getArtistName() != null
                    ? artist.getArtistName() : "";
        });
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

    private static Integer toInt(Object val) {
        if (val == null) return null;
        if (val instanceof Number n) return n.intValue();
        try { return Integer.parseInt(val.toString()); } catch (NumberFormatException e) { return null; }
    }
}
