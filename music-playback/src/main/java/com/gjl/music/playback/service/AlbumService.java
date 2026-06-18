package com.gjl.music.playback.service;

import com.gjl.music.model.Album;
import com.gjl.music.model.Song;

import java.util.Map;


public interface AlbumService {

    Map<String, Object> getAlbums(String sort, String letter, Boolean starred,
                                  int limit, int offset, String username);

    Map<String, Object> getAlbumLetters(Boolean starred, String username);

    Map<String, Object> getAlbum(Long id);

    Map<String, Object> getAlbumSongs(Long id);

    Map<String, Object> toAlbumMap(Album a);

    String resolveArtistName(Integer artistId);

    Map<String, Object> toSongMap(Song s);
}
