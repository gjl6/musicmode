package com.gjl.music.playback.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;


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
