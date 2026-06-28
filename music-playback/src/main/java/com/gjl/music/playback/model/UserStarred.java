package com.gjl.music.playback.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 用户收藏 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserStarred {
    private Long userId;
    private Long itemId;
    private String itemType;
    private LocalDateTime starredAt;
}
