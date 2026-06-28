package com.gjl.music.playback.service;

import com.gjl.music.playback.infra.subsonic.SubsonicRequestParams;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Map;

/**
 * Subsonic API 业务逻辑接口。
 */
public interface SubsonicService {

    // System
    Map<String, Object> ping();
    Map<String, Object> getLicense();
    Map<String, Object> getNowPlaying();

    // OpenSubsonic
    Map<String, Object> getOpenSubsonicExtensions();

    // Browsing
    Map<String, Object> getMusicFolders();
    Map<String, Object> getIndexes(SubsonicRequestParams p);
    Map<String, Object> getArtists(SubsonicRequestParams p);
    Map<String, Object> getMusicDirectory(SubsonicRequestParams p);
    Map<String, Object> getArtist(SubsonicRequestParams p);
    Map<String, Object> getArtistInfo(SubsonicRequestParams p);
    Map<String, Object> getArtistInfo2(SubsonicRequestParams p);
    Map<String, Object> getAlbumInfo2(SubsonicRequestParams p);
    Map<String, Object> getAlbum(SubsonicRequestParams p);
    Map<String, Object> getSong(SubsonicRequestParams p);
    Map<String, Object> getGenres();

    // Album Lists
    Map<String, Object> getAlbumList(SubsonicRequestParams p);
    Map<String, Object> getRandomSongs(SubsonicRequestParams p);
    Map<String, Object> getSongsByGenre(SubsonicRequestParams p);
    Map<String, Object> getSimilarSongs(SubsonicRequestParams p);
    Map<String, Object> getTopSongs(SubsonicRequestParams p);
    Map<String, Object> getSongs(SubsonicRequestParams p);
    Map<String, Object> getSongLetters();

    // Searching
    Map<String, Object> search(SubsonicRequestParams p);
    Map<String, Object> searchLegacy(SubsonicRequestParams p);

    // Playlists
    Map<String, Object> getPlaylists(SubsonicRequestParams p);
    Map<String, Object> getPlaylist(SubsonicRequestParams p);
    Map<String, Object> createPlaylist(SubsonicRequestParams p, HttpServletRequest request);
    Map<String, Object> deletePlaylist(SubsonicRequestParams p);
    Map<String, Object> updatePlaylist(SubsonicRequestParams p, HttpServletRequest request);

    // Media Retrieval
    void getCoverArt(SubsonicRequestParams p, HttpServletResponse response) throws IOException;
    void stream(SubsonicRequestParams p, HttpServletRequest request, HttpServletResponse response) throws IOException;
    void download(SubsonicRequestParams p, HttpServletRequest request, HttpServletResponse response) throws IOException;
    void getAvatar(SubsonicRequestParams p, HttpServletResponse response) throws IOException;

    // Lyrics
    Map<String, Object> getLyrics(SubsonicRequestParams p);
    Map<String, Object> getLyricsBySongId(SubsonicRequestParams p);

    // User
    Map<String, Object> getUser(SubsonicRequestParams p);
    Map<String, Object> getUsers(SubsonicRequestParams p);
    Map<String, Object> changePassword(SubsonicRequestParams p);

    // Library Scanning
    Map<String, Object> getPlayQueue(SubsonicRequestParams p);
    Map<String, Object> savePlayQueue(SubsonicRequestParams p, HttpServletRequest request);
    Map<String, Object> getScanStatus(SubsonicRequestParams p);
    Map<String, Object> startScan(SubsonicRequestParams p);

    // User Data
    Map<String, Object> scrobble(SubsonicRequestParams p);
    Map<String, Object> reportPlayback(SubsonicRequestParams p);
    Map<String, Object> star(SubsonicRequestParams p);
    Map<String, Object> unstar(SubsonicRequestParams p);
    Map<String, Object> getStarred(SubsonicRequestParams p);
    Map<String, Object> getStarred2(SubsonicRequestParams p);
    Map<String, Object> setRating(SubsonicRequestParams p);

    /** 清理当前请求的 ThreadLocal 缓存（Controller 在 finally 中调用） */
    void cleanupRequest();
}
