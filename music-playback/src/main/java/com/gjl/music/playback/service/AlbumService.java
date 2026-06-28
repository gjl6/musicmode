package com.gjl.music.playback.service;

import com.gjl.music.dto.AlbumResult;
import com.gjl.music.dto.SongResult;
import com.gjl.music.model.Album;
import com.gjl.music.model.Song;

import java.util.Map;

/**
 * 专辑业务逻辑接口。
 */
public interface AlbumService {

    Map<String, Object> getAlbums(String sort, String letter, Boolean starred,
                                  int limit, int offset, String username);

    Map<String, Object> getAlbumLetters(Boolean starred, String username);

    Map<String, Object> getAlbum(Long id);

    Map<String, Object> getAlbumSongs(Long id);

    /** 将 Album 转为统一的 AlbumResult DTO（coverArt 由调用方通过 {@code toBuilder().coverArt(...)} 补充） */
    AlbumResult toAlbumResult(Album a);

    String resolveArtistName(Integer artistId);

    /** 将 Song 转为统一的 SongResult DTO */
    SongResult toSongResult(Song s);
}
