package com.gjl.music.playback.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 播放计数预聚合 — user+item+type 唯一 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayCount {
    private Long userId;
    private Long itemId;
    private String itemType;
    private Integer playCount;
    private LocalDateTime lastPlayedAt;
}
