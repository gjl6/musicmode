package com.gjl.music.playback.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 播放列表 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Playlist {
    private Long id;
    private String name;
    private String sortName;
    private String comment;
    private String coverPath;
    private String owner;
    private Long ownerId;
    private boolean isPublic;
    private int songCount;
    private double duration;
    private Long totalSize;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
