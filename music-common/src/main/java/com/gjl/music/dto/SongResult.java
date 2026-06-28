package com.gjl.music.dto;

import com.gjl.music.model.Song;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 歌曲数据传输对象 — 字段名匹配前端 SongTable 组件。
 *
 * <p>覆盖 playback（浏览/搜索/Subsonic）和 editor（歌曲编辑）的 API 返回。
 * {@link #fromSong(Song)} 映射实体基本字段，coverArt/genre/contentType 由 Service 按需补充。
 */
@Getter
@Builder(toBuilder = true)
public class SongResult {

    private String id;
    private String title;
    private String artist;
    private Integer artistId;

    /** 全部艺术家列表（OpenSubsonic 扩展），按 sort_order 排序，主艺术家在首位。非 DB 映射字段，由 Service 层批量填充。 */
    private List<ArtistRef> artists;

    /** 格式化多艺术家显示名，如 "周杰伦 & 蔡依林"；单艺术家时等于 artist。非 DB 映射字段，由 Service 层填充。 */
    private String displayArtist;

    private String album;
    private Integer albumId;
    private String coverArt;
    private Integer duration;
    private Long size;
    private Integer bitRate;
    private String suffix;
    private String fileFormat;
    private Integer year;
    private Integer trackNumber;
    private Integer discNumber;
    private String genre;
    private String path;
    private String contentType;
    private LocalDateTime created;

    // ── 静态工厂 ──

    /**
     * 从 Song 实体创建 DTO（不含 coverArt、genre、contentType — 由调用方通过
     * {@link #toBuilder()} 补充）。
     */
    public static SongResult fromSong(Song s) {
        if (s == null) return null;
        return SongResult.builder()
                .id(s.getId())
                .title(s.getTitle())
                .artist(s.getArtistName())
                .artistId(s.getArtistId())
                .album(s.getAlbumName())
                .albumId(s.getAlbumId())
                .duration(s.getDuration())
                .size(s.getFileSize() > 0 ? s.getFileSize() : null)
                .bitRate(s.getBitrate())
                .suffix(s.getFileFormat())
                .fileFormat(s.getFileFormat())
                .year(parseYear(s.getYear()))
                .trackNumber(s.getTrackNumber())
                .discNumber(s.getDiscNumber())
                .path(s.getFilePath())
                .created(s.getCreateTime())
                .build();
    }

    // ── Subsonic XML/JSON 兼容 ──

    /** 转为 Subsonic API 所需的 Map 格式 */
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("title", title);
        m.put("artist", artist);
        m.put("artistId", artistId != null ? String.valueOf(artistId) : null);
        m.put("album", album);
        m.put("albumId", albumId != null ? String.valueOf(albumId) : null);
        m.put("coverArt", coverArt);
        m.put("duration", duration);
        m.put("size", size);
        m.put("suffix", suffix);
        m.put("bitRate", bitRate);
        m.put("year", year);
        m.put("track", trackNumber);
        m.put("discNumber", discNumber);
        m.put("genre", genre);
        m.put("path", path);
        m.put("contentType", contentType);
        m.put("created", created != null ? created.toString() : null);
        // OpenSubsonic 多艺术家扩展
        if (artists != null && !artists.isEmpty()) {
            m.put("artists", Map.of("artist", artists.stream()
                    .filter(a -> !a.isEmpty())
                    .map(a -> Map.of("id", a.id(), "name", a.name()))
                    .toList()));
        }
        if (displayArtist != null && !displayArtist.isBlank()) {
            m.put("displayArtist", displayArtist);
        }
        return m;
    }

    // ── helpers ──

    private static Integer parseYear(String year) {
        if (year == null || year.isBlank()) return null;
        try { return Integer.valueOf(year.trim()); } catch (NumberFormatException e) { return null; }
    }

    // ── 批量艺术家解析（Service 层共用）──

    /**
     * 将 {@link com.gjl.music.mapper.SongMapper#findSongArtistsBySongIds} 的批量查询结果
     * 按 songId 分组为 {@code Map<Long, List<ArtistRef>>}。
     *
     * @param rows 每行含 songId / artistId / artistName / sortOrder（已按 sort_order 排序）
     */
    public static Map<Long, List<ArtistRef>> groupArtistsBySongId(List<Map<String, Object>> rows) {
        Map<Long, List<ArtistRef>> result = new LinkedHashMap<>();
        if (rows == null || rows.isEmpty()) return result;
        for (Map<String, Object> row : rows) {
            // H2 数据库默认将未引用的列别名转为大写，故先查大写再查小写
            Object sid = row.get("SONGID");
            if (sid == null) sid = row.get("songId");
            if (sid == null) continue;
            Long songId = ((Number) sid).longValue();
            Object aid = row.get("ARTISTID");
            if (aid == null) aid = row.get("artistId");
            Object aname = row.get("ARTISTNAME");
            if (aname == null) aname = row.get("artistName");
            ArtistRef ref = ArtistRef.of(aid, aname != null ? aname.toString() : "");
            result.computeIfAbsent(songId, k -> new ArrayList<>()).add(ref);
        }
        return result;
    }

    /**
     * 为单个 SongResult 填充 artists + displayArtist（从预查询的批量 Map 中取出）。
     *
     * @param r         基本 DTO（fromSong 之后）
     * @param artistMap songId → List&lt;ArtistRef&gt; 的批量映射
     * @return 补全了 artists / displayArtist 的 DTO（或原样返回，若无匹配）
     */
    public static SongResult enrichArtists(SongResult r, Map<Long, List<ArtistRef>> artistMap) {
        return enrichArtists(r, artistMap, " & ");
    }

    /**
     * 为单个 SongResult 填充 artists + displayArtist。
     *
     * @param joinSeparator 多艺术家连接符，如 " & "、" / " 等
     */
    public static SongResult enrichArtists(SongResult r, Map<Long, List<ArtistRef>> artistMap, String joinSeparator) {
        if (r == null || r.getId() == null || artistMap == null || artistMap.isEmpty()) return r;
        List<ArtistRef> artists = artistMap.get(Long.parseLong(r.getId()));
        if (artists == null || artists.isEmpty()) return r;
        String sep = joinSeparator != null && !joinSeparator.isEmpty() ? joinSeparator : " & ";
        return r.toBuilder()
                .artists(artists)
                .displayArtist(artists.stream()
                        .map(ArtistRef::name)
                        .collect(Collectors.joining(sep)))
                .build();
    }
}
