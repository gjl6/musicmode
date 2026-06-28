package com.gjl.music.dto;

import com.gjl.music.model.Album;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 专辑数据传输对象 — 字段名匹配前端 AlbumCard 组件。
 *
 * <p>覆盖 playback（浏览/搜索/Subsonic）和 editor（专辑管理工作台）的 API 返回。
 * {@link #fromAlbum(Album)} 映射实体基本字段，artist/coverArt/genre 由 Service 按需补充。
 */
@Getter
@Builder(toBuilder = true)
public class AlbumResult {

    private String id;
    private String name;
    private String artist;
    private Integer artistId;
    private Integer year;
    private Integer songCount;
    private Integer duration;
    private String coverArt;
    private String genre;
    private String albumType;
    private String introduction;
    private String company;
    private String language;
    private String enrichSource;
    private LocalDateTime created;

    // ── 静态工厂 ──

    /**
     * 从 Album 实体创建 DTO（不含 artist、coverArt、genre — 由调用方通过
     * {@link #toBuilder()} 补充）。
     */
    public static AlbumResult fromAlbum(Album a) {
        if (a == null) return null;
        return AlbumResult.builder()
                .id(a.getId())
                .name(a.getAlbumName())
                .artistId(a.getArtistId())
                .year(a.getAlbumYear())
                .songCount(a.getSongCount())
                .albumType(a.getAlbumType() != null ? a.getAlbumType().name() : null)
                .introduction(a.getIntroduction())
                .company(a.getCompany())
                .language(a.getLanguage())
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
        m.put("artist", artist);
        m.put("artistId", artistId != null ? String.valueOf(artistId) : null);
        m.put("year", year);
        m.put("songCount", songCount);
        m.put("duration", duration);
        m.put("coverArt", coverArt);
        m.put("genre", genre);
        m.put("albumType", albumType);
        m.put("introduction", introduction);
        m.put("company", company);
        m.put("language", language);
        m.put("created", created != null ? created.toString() : null);
        return m;
    }
}
