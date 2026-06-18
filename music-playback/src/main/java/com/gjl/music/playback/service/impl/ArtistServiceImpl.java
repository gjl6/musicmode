package com.gjl.music.playback.service.impl;

import com.gjl.music.mapper.MusicMapper;
import com.gjl.music.model.Album;
import com.gjl.music.model.Artist;
import com.gjl.music.model.Song;
import com.gjl.music.playback.service.ArtistService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ArtistServiceImpl implements ArtistService {

    private final MusicMapper musicMapper;

    public ArtistServiceImpl(MusicMapper musicMapper) {
        this.musicMapper = musicMapper;
    }

    public Map<String, Object> getArtist(Long id) {
        Artist artist = musicMapper.findArtistById(id);
        if (artist == null) return null;
        return Map.of("artist", toArtistMap(artist));
    }

    public Map<String, Object> getArtistAlbums(Long id, String sort, String letter,
                                                int limit, int offset, Long userId) {
        limit = Math.min(limit, 500);
        List<Album> albums = musicMapper.findAlbumsByType(
                sort, offset, limit, userId, letter, null, null, id);
        int total = musicMapper.countAlbumsByLetter(letter, null, null, id);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("albums", albums.stream().map(this::toAlbumMap).collect(Collectors.toList()));
        result.put("total", total);
        return result;
    }

    public Map<String, Object> getArtistSongs(Long id, String letter, String sort,
                                               int limit, int offset, Long userId) {
        limit = Math.min(limit, 500);
        List<Song> songs = musicMapper.findSongsPaginated(
                offset, limit, letter, sort, userId, id);
        int total = musicMapper.countSongs(letter, id);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("songs", songs.stream().map(this::toSongMap).collect(Collectors.toList()));
        result.put("total", total);
        return result;
    }

    public Long resolveUserId(String username) {
        if (username == null) return null;
        return musicMapper.findUserIdByUsername(username);
    }


    public Map<String, Object> toArtistMap(Artist a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("name", a.getArtistName() != null ? a.getArtistName() : "");
        m.put("coverArt", "artist-" + a.getId());
        m.put("albumCount", a.getAlbumCount() != null ? a.getAlbumCount() : 0);
        m.put("songCount", a.getSongCount() != null ? a.getSongCount() : 0);
        m.put("introduction", a.getIntroduction());
        m.put("gender", a.getGender());
        m.put("country", a.getCountry());
        m.put("created", a.getCreateTime());
        return m;
    }

    public Map<String, Object> toAlbumMap(Album a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("name", a.getAlbumName() != null ? a.getAlbumName() : "");
        m.put("coverArt", "album-" + a.getId());
        m.put("songCount", a.getSongCount() != null ? a.getSongCount() : 0);
        m.put("year", a.getAlbumYear() != null ? a.getAlbumYear() : 0);
        m.put("genre", a.getAlbumType() != null ? a.getAlbumType().getDisplayName() : null);
        return m;
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
