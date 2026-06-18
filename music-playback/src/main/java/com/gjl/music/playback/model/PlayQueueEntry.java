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
public class PlayQueueEntry {
    private Long id;
    private String username;
    private Long songId;
    private int position;
    private LocalDateTime addedAt;
}
