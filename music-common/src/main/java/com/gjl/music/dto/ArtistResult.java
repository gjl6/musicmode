package com.gjl.music.dto;

import com.gjl.music.model.Artist;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 艺术家数据传输对象 — 字段名匹配前端 ArtistCard 组件。
 *
 * <p>覆盖 playback（浏览/搜索/Subsonic）和 editor（艺术家管理工作台）的 API 返回。
 * {@link #fromArtist(Artist)} 映射实体基本字段，coverArt 由 Service 按需补充。
 */
@Getter
@Builder(toBuilder = true)
public class ArtistResult {

    private String id;
    private String name;
    private String coverArt;
    private Integer gender;
    private String country;
    private Integer albumCount;
    private Integer songCount;
    private String introduction;
    private String enrichSource;
    private LocalDateTime created;

    // ── 静态工厂 ──

    /**
     * 从 Artist 实体创建 DTO（不含 coverArt — 由调用方通过 {@link #toBuilder()} 补充）。
     */
    public static ArtistResult fromArtist(Artist a) {
        if (a == null) return null;
        return ArtistResult.builder()
                .id(a.getId())
                .name(a.getArtistName())
                .gender(a.getGender())
                .country(a.getCountry())
                .albumCount(a.getAlbumCount())
                .songCount(a.getSongCount())
                .introduction(a.getIntroduction())
                .enrichSource(a.getEnrichSource())
                .created(a.getCreateTime())
                .build();
    }

    // ── Subsonic XML/JSON 兼容 ──

    /** 转为 Subsonic API 所需的 Map 格式 */
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("coverArt", coverArt);
        m.put("albumCount", albumCount);
        m.put("songCount", songCount);
        m.put("gender", gender);
        m.put("country", country);
        m.put("introduction", introduction);
        m.put("created", created != null ? created.toString() : null);
        return m;
    }
}
