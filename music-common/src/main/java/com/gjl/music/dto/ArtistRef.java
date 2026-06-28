package com.gjl.music.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * 艺术家引用 — OpenSubsonic artists/albumArtists 数组元素。
 *
 * <p>Jackson 序列化为 {@code {"id": "1", "name": "周杰伦"}}。
 * 使用 record 而非 Map — 类型安全，Subsonic XML/JSON 序列化开箱即用。
 */
public record ArtistRef(String id, String name) {

    /** 从原始类型创建（id 可能是 Integer/Long） */
    public static ArtistRef of(Object id, String name) {
        return new ArtistRef(
                id != null ? String.valueOf(id) : "",
                name != null ? name : ""
        );
    }

    /** 仅在 name 为空时视为空（id 可能为 0 表示未知艺术家） */
    @JsonIgnore
    public boolean isEmpty() {
        return name == null || name.isBlank();
    }
}
