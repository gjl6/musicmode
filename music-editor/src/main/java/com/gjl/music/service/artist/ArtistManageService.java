package com.gjl.music.service.artist;

import com.gjl.music.dto.ArtistResult;
import com.gjl.music.model.Artist;

import java.util.List;
import java.util.Map;

/**
 * 艺术家管理服务接口 — 列表/搜索 + 重复检测 + 单条 CRUD。
 */
public interface ArtistManageService {

    // ── 列表/搜索 ──

    Map<String, Object> listArtists(String mode, String keyword, String letter,
                                    Integer minSongs, Integer maxSongs,
                                    String style, String country,
                                    List<Long> ids,
                                    String sort, int offset, int limit);

    List<Map<String, Object>> findDuplicates();

    /** 将 Artist 转为统一的 ArtistResult DTO */
    ArtistResult toArtistResult(Artist a);

    // ── 单条 CRUD（供 Controller enrich/search/apply 端点使用，避免越层直接注入 Mapper）──

    /** 按 ID 查单个艺术家 */
    Artist findArtistById(Long id);

    /** 更新艺术家全部可编辑字段 */
    void updateArtist(Long id, String artistName, String introduction,
                      Integer gender, String country,
                      String artistCover, String enrichSource);
}
