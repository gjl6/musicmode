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
public class PlayHistory {
    private Long id;
    private Long userId;
    private Long songId;
    private LocalDateTime playedAt;
    private String source;
    private Integer durationSeconds;
}
