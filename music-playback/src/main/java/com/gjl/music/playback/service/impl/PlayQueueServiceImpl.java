package com.gjl.music.playback.service.impl;

import com.gjl.music.model.Song;
import com.gjl.music.playback.mapper.PlayQueueMapper;
import com.gjl.music.playback.model.PlayQueueEntry;
import com.gjl.music.playback.service.PlayQueueService;
import com.gjl.music.mapper.SongMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 播放队列服务。
 */
@Slf4j
@Service
public class PlayQueueServiceImpl implements PlayQueueService {

    private final PlayQueueMapper queueMapper;
    private final SongMapper songMapper;

    public PlayQueueServiceImpl(PlayQueueMapper queueMapper, SongMapper songMapper) {
        this.queueMapper = queueMapper;
        this.songMapper = songMapper;
    }

    /** 获取用户的播放队列（含歌曲详情） */
    @Override
    public List<Song> getQueue(String username) {
        List<PlayQueueEntry> entries = queueMapper.findByUsername(username);
        if (entries.isEmpty()) return List.of();

        List<Long> songIds = entries.stream()
                .map(PlayQueueEntry::getSongId)
                .toList();
        return songMapper.findSongsByIds(songIds);
    }

    /** 获取原始队列条目（含 position） */
    @Override
    public List<PlayQueueEntry> getEntries(String username) {
        return queueMapper.findByUsername(username);
    }

    /** 保存（替换）整个播放队列 */
    @Override
    @Transactional
    public void saveQueue(String username, List<Long> songIds) {
        queueMapper.clearByUsername(username);

        List<PlayQueueEntry> entries = new ArrayList<>();
        int pos = 0;
        for (Long songId : songIds) {
            entries.add(PlayQueueEntry.builder()
                    .username(username)
                    .songId(songId)
                    .position(pos++)
                    .build());
        }
        if (!entries.isEmpty()) {
            queueMapper.batchInsert(entries);
        }
        log.debug("播放队列保存: user={}, tracks={}", username, songIds.size());
    }

    /** 从队列中移除指定位置 */
    @Override
    @Transactional
    public void remove(String username, int position) {
        queueMapper.deleteByUsernameAndPosition(username, position);
    }

    /** 清空队列 */
    @Override
    @Transactional
    public void clear(String username) {
        queueMapper.clearByUsername(username);
    }

    /** Subsonic getNowPlaying — 获取最近活跃的播放会话 */
    @Override
    public List<Song> getNowPlaying() {
        // 获取最近更新的队列条目（跨用户，最近的活跃 session）
        List<PlayQueueEntry> entries = queueMapper.findRecentActive(50);
        if (entries.isEmpty()) return List.of();

        List<Long> songIds = entries.stream()
                .map(PlayQueueEntry::getSongId)
                .distinct()
                .toList();
        return songMapper.findSongsByIds(songIds);
    }

    /** Subsonic getPlayQueue — 带位置信息 */
    @Override
    public QueueWithPosition getQueueWithPosition(String username) {
        List<PlayQueueEntry> entries = queueMapper.findByUsername(username);
        List<Long> songIds = entries.stream()
                .map(PlayQueueEntry::getSongId)
                .toList();
        List<Song> songs = songIds.isEmpty() ? List.of() : songMapper.findSongsByIds(songIds);

        // 查找当前播放位置（最近播放的条目）
        int currentPos = -1;
        long positionMs = 0;
        String changedBy = username;
        java.time.LocalDateTime changedAt = null;

        // 取 first entry 的时间作为 changedAt
        if (!entries.isEmpty()) {
            changedAt = entries.get(0).getAddedAt();
        }

        return new QueueWithPosition(songs, currentPos, positionMs, changedBy, changedAt);
    }
}
