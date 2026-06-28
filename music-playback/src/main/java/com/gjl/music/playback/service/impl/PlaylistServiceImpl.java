package com.gjl.music.playback.service.impl;
import com.gjl.music.infra.util.PathUtils;
import com.gjl.music.infra.util.PinyinUtils;
import com.gjl.music.playback.infra.m3u.M3uParser;
import com.gjl.music.playback.service.PlaylistService;
import com.gjl.music.mapper.SongMapper;
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
    private final SongMapper songMapper;
    private final Path coversDir;

    public PlaylistServiceImpl(PlaylistMapper playlistMapper,
                               PlaylistTrackMapper trackMapper,
                               SongMapper songMapper,
                               @Value("${music.covers-dir:./covers}") String coversDir) {
        this.playlistMapper = playlistMapper;
        this.trackMapper = trackMapper;
        this.songMapper = songMapper;
        this.coversDir = Path.of(coversDir).toAbsolutePath().normalize();
    }

    // ══════════════════════════════════════════════════
    // 权限校验
    // ══════════════════════════════════════════════════

    private void checkOwnership(Playlist pl, String username) {
        if (pl == null) throw new IllegalArgumentException("播放列表不存在");
        if (username != null && !pl.getOwner().equals(username)) {
            throw new AccessDeniedException("无权操作此播放列表");
        }
    }

    // ══════════════════════════════════════════════════
    // 查询
    // ══════════════════════════════════════════════════

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

        // 按 track 顺序保留歌曲顺序（findSongsByIds 不保证顺序）
        return songsInTrackOrder(tracks);
    }

    @Override
    public List<Song> getFirstSongs(Long playlistId, int limit) {
        List<PlaylistTrack> tracks = trackMapper.findFirstNByPlaylistId(playlistId, limit);
        if (tracks.isEmpty()) return List.of();

        return songsInTrackOrder(tracks);
    }

    /** 按 track position 顺序返回歌曲列表（findSongsByIds 不保证 IN 子句顺序） */
    private List<Song> songsInTrackOrder(List<PlaylistTrack> tracks) {
        List<Long> songIds = tracks.stream()
                .map(PlaylistTrack::getSongId)
                .toList();
        Map<Long, Song> songMap = songMapper.findSongsByIds(songIds).stream()
                .collect(Collectors.toMap(s -> Long.valueOf(s.getId()), s -> s, (a, b) -> a));
        return songIds.stream()
                .map(songMap::get)
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public Map<Long, List<Song>> getFirstSongsBatch(List<Long> playlistIds, int limit) {
        if (playlistIds == null || playlistIds.isEmpty()) return Map.of();

        // 1 条 SQL：批量查所有歌单前 N 条曲目记录
        List<PlaylistTrack> allTracks = trackMapper.findFirstNByPlaylistIds(playlistIds, limit);
        if (allTracks.isEmpty()) return Map.of();

        // 按 playlistId 分组
        Map<Long, List<Long>> plSongIds = new LinkedHashMap<>();
        for (PlaylistTrack t : allTracks) {
            plSongIds.computeIfAbsent(t.getPlaylistId(), k -> new ArrayList<>())
                    .add(t.getSongId());
        }

        // 1 条 SQL：批量查所有相关歌曲
        List<Long> allSongIds = allTracks.stream()
                .map(PlaylistTrack::getSongId).distinct().toList();
        Map<Long, Song> songMap = songMapper.findSongsByIds(allSongIds).stream()
                .collect(Collectors.toMap(s -> Long.valueOf(s.getId()), s -> s, (a, b) -> a));

        // 组装结果
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
        // 权限过滤：只返回自己的 + 公开的
        return all.stream()
                .filter(pl -> pl.getOwner().equals(username) || pl.isPublic())
                .toList();
    }

    // ══════════════════════════════════════════════════
    // CRUD
    // ══════════════════════════════════════════════════

    @Override
    @Transactional
    public Playlist create(String name, String comment, Long ownerId, boolean isPublic,
                           String coverPath) {
        return createByUsername(name, comment, String.valueOf(ownerId), ownerId, isPublic,
                coverPath);
    }

    /** 按用户名 + userId 创建播放列表 */
    @Transactional
    public Playlist createByUsername(String name, String comment, String username,
                                     Long userId, boolean isPublic) {
        return createByUsername(name, comment, username, userId, isPublic, null);
    }

    /** 按用户名 + userId 创建播放列表（含封面路径） */
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

    // ══════════════════════════════════════════════════
    // 歌曲管理
    // ══════════════════════════════════════════════════

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

    // ══════════════════════════════════════════════════
    // 排序（大间隔算法）
    // ══════════════════════════════════════════════════

    @Override
    @Transactional
    public void reorderSong(Long playlistId, int fromPosition, int toPosition) {
        Playlist pl = playlistMapper.findById(playlistId);
        checkOwnership(pl, pl != null ? pl.getOwner() : null);

        if (fromPosition == toPosition) return;

        // fromPosition / toPosition 是 1-based 显示序号，需转成 DB 大间隔 position
        List<PlaylistTrack> sorted = trackMapper.findByPlaylistId(playlistId).stream()
                .sorted(Comparator.comparingInt(PlaylistTrack::getPosition))
                .toList();

        if (fromPosition < 1 || fromPosition > sorted.size()) {
            throw new IllegalArgumentException("position " + fromPosition + " 超出范围");
        }

        // 找到实际要移动的 track（by 显示序号）
        PlaylistTrack moving = sorted.get(fromPosition - 1);
        int actualFromPos = moving.getPosition();

        // 需要先 renumber 则重排后再递归调用一次（此时需用新的序号）
        boolean needRenumber = needRenumber(sorted, fromPosition, toPosition);
        if (needRenumber) {
            renumberInternal(playlistId);
            // 重新加载排序列表，递归计算新位置
            reorderSong(playlistId, fromPosition, toPosition);
            return;
        }

        // 计算目标 DB position
        int newPos = calcNewPosition(sorted, fromPosition, toPosition);

        trackMapper.updatePosition(playlistId, actualFromPos, newPos);
    }

    /** 判断是否需要 renumber（插入区间不够用） */
    private boolean needRenumber(List<PlaylistTrack> sorted, int from, int to) {
        if (to <= 1) {
            return sorted.getFirst().getPosition() / 2 < 1;
        }
        if (to >= sorted.size()) {
            return false; // 放末尾永远有空间
        }
        int prevIdx = from < to ? to - 1 : to - 2;
        int nextIdx = from < to ? to     : to - 1;
        int prevPos = sorted.get(prevIdx).getPosition();
        int nextPos = sorted.get(nextIdx).getPosition();
        return nextPos - prevPos <= 1;
    }

    /** 计算目标 DB position（1-based 显示序号 → 大间隔 position） */
    private int calcNewPosition(List<PlaylistTrack> sorted, int from, int to) {
        if (to <= 1) {
            int firstPos = sorted.getFirst().getPosition();
            int np = firstPos / 2;
            return Math.max(np, 1);
        }
        if (to >= sorted.size()) {
            return sorted.getLast().getPosition() + RENUMBER_INTERVAL;
        }
        // 向下移动(from<to)：移除 from 后中间项上移，邻居索引 +1
        int prevIdx = from < to ? to - 1 : to - 2;
        int nextIdx = from < to ? to     : to - 1;
        int prevPos = sorted.get(prevIdx).getPosition();
        int nextPos = sorted.get(nextIdx).getPosition();
        int gap = nextPos - prevPos;
        int np = prevPos + gap / 2;
        return Math.max(np, prevPos + 1);
    }

    /**
     * 全量替换播放列表歌曲顺序（前端排序后一次落盘）。
     * 简单策略：删光旧记录 → 按传入顺序重插，position 从 RENUMBER_INTERVAL 开始递增。
     */
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

    // ══════════════════════════════════════════════════
    // M3U 导入/导出
    // ══════════════════════════════════════════════════

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
            path = PathUtils.normalize(path);

            Song song = songMapper.findSongByFilePath(path);
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

    // ══════════════════════════════════════════════════
    // 封面上传
    // ══════════════════════════════════════════════════

    @Override
    @Transactional
    public String uploadCover(Long playlistId, MultipartFile file) throws IOException {
        Playlist pl = playlistMapper.findById(playlistId);
        if (pl == null) throw new IllegalArgumentException("播放列表不存在: " + playlistId);

        // 确保 covers 目录存在
        Files.createDirectories(coversDir);

        // 生成文件名：playlist-{id}-{timestamp}.{ext}
        String originalName = file.getOriginalFilename();
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf('.'));
        }
        String fileName = "playlist-" + playlistId + "-" + System.currentTimeMillis() + ext;
        Path dest = coversDir.resolve(fileName);

        // 安全检查：防止路径穿越
        if (!dest.normalize().startsWith(coversDir)) {
            throw new IOException("封面路径非法: " + fileName);
        }

        file.transferTo(dest.toFile());

        // 存储相对路径
        String relativePath = fileName;
        pl.setCoverPath(relativePath);
        playlistMapper.update(pl);

        log.info("歌单封面上传: plId={}, path={}", playlistId, relativePath);
        return relativePath;
    }
}
