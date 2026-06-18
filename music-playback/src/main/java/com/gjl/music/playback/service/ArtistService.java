package com.gjl.music.playback.service;

import com.gjl.music.model.Album;
import com.gjl.music.model.Artist;
import com.gjl.music.model.Song;

import java.util.Map;


public interface ArtistService {

    Map<String, Object> getArtist(Long id);

    Map<String, Object> getArtistAlbums(Long id, String sort, String letter,
                                        int limit, int offset, Long userId);

    Map<String, Object> getArtistSongs(Long id, String letter, String sort,
                                       int limit, int offset, Long userId);

    Long resolveUserId(String username);

    Map<String, Object> toArtistMap(Artist a);

    Map<String, Object> toAlbumMap(Album a);

    Map<String, Object> toSongMap(Song s);
}
