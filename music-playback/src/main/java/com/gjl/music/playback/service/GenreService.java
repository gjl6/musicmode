package com.gjl.music.playback.service;

import com.gjl.music.dto.SongResult;
import com.gjl.music.model.Song;

import java.util.Map;

/**
 * 流派业务逻辑接口。
 */
public interface GenreService {

    Map<String, Object> getGenres(String sort, String letter, int limit, int offset);

    Map<String, Object> getGenreLetters();

    Map<String, Object> getGenreSongs(String name, String letter, String sort,
                                      int limit, int offset, Long userId);

    Long resolveUserId(String username);

    SongResult toSongResult(Song s);
}
