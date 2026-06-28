package com.gjl.music.playback.service;

import com.gjl.music.model.Song;
import com.gjl.music.playback.model.PlayQueueEntry;

import java.util.List;

/**
 * 播放队列服务接口。
 */
public interface PlayQueueService {

    List<Song> getQueue(String username);

    List<PlayQueueEntry> getEntries(String username);

    void saveQueue(String username, List<Long> songIds);

    void remove(String username, int position);

    void clear(String username);

    /** Subsonic getNowPlaying — 获取最近活跃的播放会话中的歌曲列表 */
    List<Song> getNowPlaying();

    /** Subsonic getPlayQueue — 带位置信息的队列数据 */
    QueueWithPosition getQueueWithPosition(String username);

    /** 队列 + 当前位置 DTO */
    record QueueWithPosition(List<Song> songs, int currentPosition,
                             long positionMillis, String changedBy,
                             java.time.LocalDateTime changedAt) {}
}
