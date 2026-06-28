package com.gjl.music.playback.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 播放器状态快照。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerState {
    private Long currentSongId;
    private String username;
    private double position;     // 当前播放位置（秒）
    private Long timestamp;      // 状态时间戳（毫秒）
    private String event;        // PLAY / PAUSE / STOP / TRACK_CHANGE / SEEK
    private int queueIndex;      // 队列中的位置（-1 表示不在队列中）
    private String queueId;      // 当前队列 ID
}
