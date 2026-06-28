package com.gjl.music.playback.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 播放列表曲目关联 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaylistTrack {
    private Long id;
    private Long playlistId;
    private Long songId;
    private int position;
    private LocalDateTime addedAt;
}
