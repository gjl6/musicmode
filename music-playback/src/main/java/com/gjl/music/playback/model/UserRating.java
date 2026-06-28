package com.gjl.music.playback.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 用户评分 — 1-5 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRating {
    private Long userId;
    private Long itemId;
    private String itemType;
    private Integer rating;
    private LocalDateTime ratedAt;
}
