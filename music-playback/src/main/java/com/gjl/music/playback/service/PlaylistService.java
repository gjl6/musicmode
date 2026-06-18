package com.gjl.music.playback.service;

import com.gjl.music.model.Song;
import com.gjl.music.playback.model.Playlist;

import java.util.List;
import java.util.Map;


public interface PlaylistService {


    List<Playlist> listByUsername(String username);


    List<Playlist> listByUser(Long userId);


    Playlist getById(Long id);


    List<Song> getSongs(Long playlistId);


    List<Song> getFirstSongs(Long playlistId, int limit);


    Map<Long, List<Song>> getFirstSongsBatch(List<Long> playlistIds, int limit);


    Playlist create(String name, String comment, Long ownerId, boolean isPublic, String coverPath);


    Playlist createByUsername(String name, String comment, String username,
                              Long userId, boolean isPublic);


    Playlist createByUsername(String name, String comment, String username,
                              Long userId, boolean isPublic, String coverPath);


    Playlist update(Long id, String name, String comment, boolean isPublic, String coverPath);


    void delete(Long id);


    void addSongs(Long playlistId, List<Long> songIds);


    void removeSong(Long playlistId, int position);


    String exportM3u(Long playlistId);


    List<Long> importM3u(Long playlistId, String m3uContent, String rootDir);


    void reorderSong(Long playlistId, int fromPosition, int toPosition);


    void reorderAll(Long playlistId, List<Long> songIds);


    List<Playlist> getPlaylistsContainingSong(String username, Long songId);


    String uploadCover(Long playlistId, org.springframework.web.multipart.MultipartFile file)
            throws java.io.IOException;
}
