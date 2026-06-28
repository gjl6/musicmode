package com.gjl.music.playback.service;

import com.gjl.music.dto.AlbumResult;
import com.gjl.music.dto.ArtistResult;
import com.gjl.music.dto.SongResult;
import com.gjl.music.model.Album;
import com.gjl.music.model.Artist;
import com.gjl.music.model.Song;

import java.util.Map;

/**
 * 艺术家业务逻辑接口。
 */
public interface ArtistService {

    Map<String, Object> getArtist(Long id);

    Map<String, Object> getArtistAlbums(Long id, String sort, String letter,
                                        int limit, int offset, Long userId);

    Map<String, Object> getArtistSongs(Long id, String letter, String sort,
                                       int limit, int offset, Long userId);

    Long resolveUserId(String username);

    ArtistResult toArtistResult(Artist a);

    AlbumResult toAlbumResult(Album a);

    SongResult toSongResult(Song s);

    /** 获取播放量最高的艺术家列表。userId=null 时为全局统计 */
    Map<String, Object> getTopArtists(Long userId, int limit);
}
