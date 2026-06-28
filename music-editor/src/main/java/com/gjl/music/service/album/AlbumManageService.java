package com.gjl.music.service.album;

import com.gjl.music.dto.AlbumResult;
import com.gjl.music.model.Album;

import java.util.List;
import java.util.Map;

/**
 * 专辑管理服务接口 — 列表/搜索 + 重复检测 + 单条 CRUD。
 */
public interface AlbumManageService {

    // ── 列表/搜索 ──

    Map<String, Object> listAlbums(String mode, String keyword, String letter,
                                   Long artistId,
                                   Integer minSongs, Integer maxSongs,
                                   List<Long> ids,
                                   String sort, int offset, int limit);

    List<Map<String, Object>> findDuplicates();

    /** 将 Album 转为统一的 AlbumResult DTO */
    AlbumResult toAlbumResult(Album a);

    // ── 单条 CRUD（供 Controller enrich/search/apply 端点使用，避免越层直接注入 Mapper）──

    /** 按 ID 查单个专辑 */
    Album findAlbumById(Long id);

    /** 更新专辑全部可编辑字段 */
    void updateAlbum(Long id, String albumName, String albumType, Integer albumYear,
                     String introduction, String company, String language,
                     String albumCover, String enrichSource);

    /** 按艺术家 ID 解析艺术家名称 */
    String resolveArtistName(Integer artistId);
}
