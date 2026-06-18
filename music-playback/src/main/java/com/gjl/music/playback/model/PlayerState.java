package com.gjl.music.playback.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerState {
    private Long currentSongId;
    private String username;
    private double position;
    private Long timestamp;
    private String event;
    private int queueIndex;
    private String queueId;
}
