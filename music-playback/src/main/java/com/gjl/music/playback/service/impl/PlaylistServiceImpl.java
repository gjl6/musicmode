package com.gjl.music.playback.service.impl;
import com.gjl.music.playback.infra.m3u.M3uParser;
import com.gjl.music.playback.service.PlaylistService;

import com.gjl.music.infra.util.PinyinUtils;
import com.gjl.music.mapper.MusicMapper;
import com.gjl.music.model.Song;
import com.gjl.music.playback.mapper.PlaylistMapper;
import com.gjl.music.playback.mapper.PlaylistTrackMapper;
import com.gjl.music.playback.model.Playlist;
import com.gjl.music.playback.model.PlaylistTrack;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PlaylistServiceImpl implements PlaylistService {

    private static final int RENUMBER_INTERVAL = 1000;

    private final PlaylistMapper playlistMapper;
    private final PlaylistTrackMapper trackMapper;
    private final MusicMapper musicMapper;
    private final Path coversDir;

    public PlaylistServiceImpl(PlaylistMapper playlistMapper,
                               PlaylistTrackMapper trackMapper,
                               MusicMapper musicMapper,
                               @Value("${music.covers-dir:./covers}") String coversDir) {
        this.playlistMapper = playlistMapper;
        this.trackMapper = trackMapper;
        this.musicMapper = musicMapper;
        this.coversDir = Path.of(coversDir).toAbsolutePath().normalize();
    }


    private void checkOwnership(Playlist pl, String username) {
        if (pl == null) throw new IllegalArgumentException("播放列表不存在");
        if (username != null && !pl.getOwner().equals(username)) {
            throw new AccessDeniedException("无权操作此播放列表");
        }
    }


    @Override
    public List<Playlist> listByUser(Long userId) {
        return playlistMapper.findByOwner(String.valueOf(userId));
    }

    public List<Playlist> listByUsername(String username) {
        return playlistMapper.findByOwner(username);
    }

    @Override
    public Playlist getById(Long id) {
        return playlistMapper.findById(id);
    }

    @Override
    public List<Song> getSongs(Long playlistId) {
        List<PlaylistTrack> tracks = trackMapper.findByPlaylistId(playlistId);
        if (tracks.isEmpty()) return List.of();

                return songsInTrackOrder(tracks);
    }

    @Override
    public List<Song> getFirstSongs(Long playlistId, int limit) {
        List<PlaylistTrack> tracks = trackMapper.findFirstNByPlaylistId(playlistId, limit);
        if (tracks.isEmpty()) return List.of();

        return songsInTrackOrder(tracks);
    }


    private List<Song> songsInTrackOrder(List<PlaylistTrack> tracks) {
        List<Long> songIds = tracks.stream()
                .map(PlaylistTrack::getSongId)
                .toList();
        Map<Long, Song> songMap = musicMapper.findSongsByIds(songIds).stream()
                .collect(Collectors.toMap(s -> Long.valueOf(s.getId()), s -> s, (a, b) -> a));
        return songIds.stream()
                .map(songMap::get)
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public Map<Long, List<Song>> getFirstSongsBatch(List<Long> playlistIds, int limit) {
        if (playlistIds == null || playlistIds.isEmpty()) return Map.of();

                List<PlaylistTrack> allTracks = trackMapper.findFirstNByPlaylistIds(playlistIds, limit);
        if (allTracks.isEmpty()) return Map.of();

                Map<Long, List<Long>> plSongIds = new LinkedHashMap<>();
        for (PlaylistTrack t : allTracks) {
            plSongIds.computeIfAbsent(t.getPlaylistId(), k -> new ArrayList<>())
                    .add(t.getSongId());
        }

                List<Long> allSongIds = allTracks.stream()
                .map(PlaylistTrack::getSongId).distinct().toList();
        Map<Long, Song> songMap = musicMapper.findSongsByIds(allSongIds).stream()
                .collect(Collectors.toMap(s -> Long.valueOf(s.getId()), s -> s, (a, b) -> a));

                Map<Long, List<Song>> result = new LinkedHashMap<>();
        for (var entry : plSongIds.entrySet()) {
            List<Song> songs = entry.getValue().stream()
                    .map(songMap::get)
                    .filter(Objects::nonNull)
                    .toList();
            if (!songs.isEmpty()) {
                result.put(entry.getKey(), songs);
            }
        }
        return result;
    }

    @Override
    public List<Playlist> getPlaylistsContainingSong(String username, Long songId) {
        List<Long> playlistIds = trackMapper.findPlaylistIdsBySongId(songId);
        if (playlistIds.isEmpty()) return List.of();
        List<Playlist> all = playlistMapper.findByIds(playlistIds);
                return all.stream()
                .filter(pl -> pl.getOwner().equals(username) || pl.isPublic())
                .toList();
    }


    @Override
    @Transactional
    public Playlist create(String name, String comment, Long ownerId, boolean isPublic,
                           String coverPath) {
        return createByUsername(name, comment, String.valueOf(ownerId), ownerId, isPublic,
                coverPath);
    }


    @Transactional
    public Playlist createByUsername(String name, String comment, String username,
                                     Long userId, boolean isPublic) {
        return createByUsername(name, comment, username, userId, isPublic, null);
    }


    @Transactional
    public Playlist createByUsername(String name, String comment, String username,
                                     Long userId, boolean isPublic, String coverPath) {
        Playlist pl = Playlist.builder()
                .name(name)
                .sortName(PinyinUtils.toSortKey(name))
                .comment(comment)
                .coverPath(coverPath)
                .owner(username)
                .ownerId(userId)
                .isPublic(isPublic)
                .build();
        playlistMapper.insert(pl);
        log.info("播放列表创建: id={}, name={}, owner={}, coverPath={}",
                pl.getId(), name, username, coverPath);
        return pl;
    }

    @Override
    @Transactional
    public Playlist update(Long id, String name, String comment, boolean isPublic,
                           String coverPath) {
        Playlist pl = playlistMapper.findById(id);
        checkOwnership(pl, pl != null ? pl.getOwner() : null);
        pl.setName(name);
        pl.setSortName(PinyinUtils.toSortKey(name));
        pl.setComment(comment);
        pl.setCoverPath(coverPath);
        pl.setPublic(isPublic);
        playlistMapper.update(pl);
        return pl;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Playlist pl = playlistMapper.findById(id);
        checkOwnership(pl, pl != null ? pl.getOwner() : null);
        trackMapper.deleteByPlaylistId(id);
        playlistMapper.deleteById(id);
        log.info("播放列表删除: id={}", id);
    }


    @Override
    @Transactional
    public void addSongs(Long playlistId, List<Long> songIds) {
        Playlist pl = playlistMapper.findById(playlistId);
        checkOwnership(pl, pl != null ? pl.getOwner() : null);

        int maxPos = trackMapper.maxPosition(playlistId);
        int pos = maxPos < RENUMBER_INTERVAL ? RENUMBER_INTERVAL : maxPos + RENUMBER_INTERVAL;

        List<PlaylistTrack> tracks = new ArrayList<>();
        for (Long songId : songIds) {
            tracks.add(PlaylistTrack.builder()
                    .playlistId(playlistId)
                    .songId(songId)
                    .position(pos)
                    .build());
            pos += RENUMBER_INTERVAL;
        }
        trackMapper.batchInsert(tracks);
        playlistMapper.updateSongCount(playlistId);
    }

    @Override
    @Transactional
    public void removeSong(Long playlistId, int position) {
        Playlist pl = playlistMapper.findById(playlistId);
        checkOwnership(pl, pl != null ? pl.getOwner() : null);

        trackMapper.deleteByPlaylistAndPosition(playlistId, position);
        renumberInternal(playlistId);
        playlistMapper.updateSongCount(playlistId);
    }


    @Override
    @Transactional
    public void reorderSong(Long playlistId, int fromPosition, int toPosition) {
        Playlist pl = playlistMapper.findById(playlistId);
        checkOwnership(pl, pl != null ? pl.getOwner() : null);

        if (fromPosition == toPosition) return;

                List<PlaylistTrack> sorted = trackMapper.findByPlaylistId(playlistId).stream()
                .sorted(Comparator.comparingInt(PlaylistTrack::getPosition))
                .toList();

        if (fromPosition < 1 || fromPosition > sorted.size()) {
            throw new IllegalArgumentException("position " + fromPosition + " 超出范围");
        }

                PlaylistTrack moving = sorted.get(fromPosition - 1);
        int actualFromPos = moving.getPosition();

                boolean needRenumber = needRenumber(sorted, fromPosition, toPosition);
        if (needRenumber) {
            renumberInternal(playlistId);
                        reorderSong(playlistId, fromPosition, toPosition);
            return;
        }

                int newPos = calcNewPosition(sorted, fromPosition, toPosition);

        trackMapper.updatePosition(playlistId, actualFromPos, newPos);
    }


    private boolean needRenumber(List<PlaylistTrack> sorted, int from, int to) {
        if (to <= 1) {
            return sorted.getFirst().getPosition() / 2 < 1;
        }
        if (to >= sorted.size()) {
            return false;
        }
        int prevIdx = from < to ? to - 1 : to - 2;
        int nextIdx = from < to ? to     : to - 1;
        int prevPos = sorted.get(prevIdx).getPosition();
        int nextPos = sorted.get(nextIdx).getPosition();
        return nextPos - prevPos <= 1;
    }


    private int calcNewPosition(List<PlaylistTrack> sorted, int from, int to) {
        if (to <= 1) {
            int firstPos = sorted.getFirst().getPosition();
            int np = firstPos / 2;
            return Math.max(np, 1);
        }
        if (to >= sorted.size()) {
            return sorted.getLast().getPosition() + RENUMBER_INTERVAL;
        }
                int prevIdx = from < to ? to - 1 : to - 2;
        int nextIdx = from < to ? to     : to - 1;
        int prevPos = sorted.get(prevIdx).getPosition();
        int nextPos = sorted.get(nextIdx).getPosition();
        int gap = nextPos - prevPos;
        int np = prevPos + gap / 2;
        return Math.max(np, prevPos + 1);
    }


    @Override
    @Transactional
    public void reorderAll(Long playlistId, List<Long> songIds) {
        Playlist pl = playlistMapper.findById(playlistId);
        checkOwnership(pl, pl != null ? pl.getOwner() : null);

        trackMapper.deleteByPlaylistId(playlistId);
        if (songIds == null || songIds.isEmpty()) {
            playlistMapper.updateSongCount(playlistId);
            return;
        }

        int pos = RENUMBER_INTERVAL;
        List<PlaylistTrack> tracks = new ArrayList<>(songIds.size());
        for (Long songId : songIds) {
            tracks.add(PlaylistTrack.builder()
                    .playlistId(playlistId)
                    .songId(songId)
                    .position(pos)
                    .build());
            pos += RENUMBER_INTERVAL;
        }
        trackMapper.batchInsert(tracks);
        playlistMapper.updateSongCount(playlistId);
        log.info("歌单全量重排: plId={}, songs={}, from={} to={}",
                playlistId, songIds.size(), RENUMBER_INTERVAL,
                pos - RENUMBER_INTERVAL);
    }

    private void renumberInternal(Long playlistId) {
        List<PlaylistTrack> tracks = trackMapper.findByPlaylistId(playlistId);
        int pos = RENUMBER_INTERVAL;
        for (PlaylistTrack t : tracks) {
            trackMapper.setPosition(playlistId, t.getId(), pos);
            pos += RENUMBER_INTERVAL;
        }
    }


    @Override
    public String exportM3u(Long playlistId) {
        List<Song> songs = getSongs(playlistId);
        List<M3uParser.M3uEntry> entries = songs.stream()
                .map(s -> new M3uParser.M3uEntry(
                        s.getFilePath(),
                        s.getTitle(),
                        s.getDuration() != null ? s.getDuration() : -1))
                .toList();
        return M3uParser.toM3u8(entries);
    }

    @Override
    @Transactional
    public List<Long> importM3u(Long playlistId, String m3uContent, String rootDir) {
        Playlist pl = playlistMapper.findById(playlistId);
        checkOwnership(pl, pl != null ? pl.getOwner() : null);

        String cleaned = m3uContent;
        if (cleaned.startsWith("﻿")) cleaned = cleaned.substring(1);
        List<M3uParser.M3uEntry> entries = M3uParser.parse(cleaned);

        List<Long> songIds = new ArrayList<>();
        List<String> failedPaths = new ArrayList<>();

        for (M3uParser.M3uEntry entry : entries) {
            String path = entry.path();
            if (rootDir != null && !path.startsWith("/") && !path.contains(":")) {
                path = rootDir + "/" + path;
            }
            path = path.replace('\\', '/');

            Song song = musicMapper.findSongByFilePath(path);
            if (song != null) {
                songIds.add(Long.valueOf(song.getId()));
            } else {
                failedPaths.add(entry.path());
                log.debug("M3U 导入: 未找到歌曲 path={}", entry.path());
            }
        }

        if (!songIds.isEmpty()) {
            addSongs(playlistId, songIds);
        }

        log.info("M3U 导入: playlist={}, entries={}, matched={}, failed={}",
                playlistId, entries.size(), songIds.size(), failedPaths.size());
        return songIds;
    }


    @Override
    @Transactional
    public String uploadCover(Long playlistId, MultipartFile file) throws IOException {
        Playlist pl = playlistMapper.findById(playlistId);
        if (pl == null) throw new IllegalArgumentException("播放列表不存在: " + playlistId);

                Files.createDirectories(coversDir);

                String originalName = file.getOriginalFilename();
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf('.'));
        }
        String fileName = "playlist-" + playlistId + "-" + System.currentTimeMillis() + ext;
        Path dest = coversDir.resolve(fileName);

                if (!dest.normalize().startsWith(coversDir)) {
            throw new IOException("封面路径非法: " + fileName);
        }

        file.transferTo(dest.toFile());

                String relativePath = fileName;
        pl.setCoverPath(relativePath);
        playlistMapper.update(pl);

        log.info("歌单封面上传: plId={}, path={}", playlistId, relativePath);
        return relativePath;
    }
}
