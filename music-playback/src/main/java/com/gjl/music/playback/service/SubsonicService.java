package com.gjl.music.playback.service;

import com.gjl.music.playback.infra.subsonic.SubsonicRequestParams;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Map;


public interface SubsonicService {

        Map<String, Object> ping();
    Map<String, Object> getLicense();

        Map<String, Object> getMusicFolders();
    Map<String, Object> getIndexes(SubsonicRequestParams p);
    Map<String, Object> getArtists(SubsonicRequestParams p);
    Map<String, Object> getMusicDirectory(SubsonicRequestParams p);
    Map<String, Object> getArtist(SubsonicRequestParams p);
    Map<String, Object> getAlbum(SubsonicRequestParams p);
    Map<String, Object> getSong(SubsonicRequestParams p);
    Map<String, Object> getGenres();

        Map<String, Object> getAlbumList(SubsonicRequestParams p);
    Map<String, Object> getRandomSongs(SubsonicRequestParams p);
    Map<String, Object> getSongsByGenre(SubsonicRequestParams p);
    Map<String, Object> getSongs(SubsonicRequestParams p);
    Map<String, Object> getSongLetters();

        Map<String, Object> search(SubsonicRequestParams p);

        Map<String, Object> getPlaylists(SubsonicRequestParams p);
    Map<String, Object> getPlaylist(SubsonicRequestParams p);
    Map<String, Object> createPlaylist(SubsonicRequestParams p, HttpServletRequest request);
    Map<String, Object> deletePlaylist(SubsonicRequestParams p);
    Map<String, Object> updatePlaylist(SubsonicRequestParams p, HttpServletRequest request);

        void getCoverArt(SubsonicRequestParams p, HttpServletResponse response) throws IOException;

        Map<String, Object> getLyricsBySongId(SubsonicRequestParams p);

        Map<String, Object> scrobble(SubsonicRequestParams p);
    Map<String, Object> reportPlayback(SubsonicRequestParams p);
    Map<String, Object> star(SubsonicRequestParams p);
    Map<String, Object> unstar(SubsonicRequestParams p);
    Map<String, Object> getStarred(SubsonicRequestParams p);
    Map<String, Object> getStarred2(SubsonicRequestParams p);
    Map<String, Object> setRating(SubsonicRequestParams p);


    void cleanupRequest();
}
