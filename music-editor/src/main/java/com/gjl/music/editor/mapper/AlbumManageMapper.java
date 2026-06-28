package com.gjl.music.editor.mapper;

import com.gjl.music.model.Album;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * Album 管理操作 Mapper（仅 editor 使用）。
 * 包含专辑搜索、重复检测、合并操作、Pipeline 扫描查询。
 */
@Mapper
public interface AlbumManageMapper {

    // ── 搜索 ──

    List<Album> searchAlbums(@Param("keyword") String keyword,
                             @Param("letter") String letter,
                             @Param("sort") String sort,
                             @Param("offset") int offset,
                             @Param("limit") int limit);

    int countSearchAlbums(@Param("keyword") String keyword,
                          @Param("letter") String letter);

    // ── 重复检测 + 合并 ──

    /** 查找大小写重复的专辑名称组（按 LOWER(album_name) 分组，cnt > 1） */
    List<Map<String, Object>> findDuplicateAlbumNames();

    /** 按归一化名称查询专辑（LOWER(album_name) = LOWER(#{normName})） */
    List<Album> findAlbumsByNormalizedName(@Param("normName") String normName);

    /** 将 source 专辑的歌曲重分配到 target（跳过已关联 target 的歌曲） */
    int reassignSongAlbum(@Param("sourceId") Long sourceId,
                           @Param("targetId") Long targetId);

    /** 删除 source 专辑的歌曲关联（仅删除该歌曲已同时关联 target 的记录） */
    int deleteConflictingSongAlbums(@Param("sourceId") Long sourceId,
                                     @Param("targetId") Long targetId);

    /** 删除专辑 */
    int deleteAlbum(@Param("id") Long id);

    // ── Pipeline 扫描查询 ──

    List<Long> findAllAlbumIds(@Param("letter") String letter,
                                @Param("keyword") String keyword,
                                @Param("artistId") Long artistId,
                                @Param("minSongs") Integer minSongs,
                                @Param("maxSongs") Integer maxSongs);

    List<Long> findAlbumIdsByLetter(@Param("letter") String letter,
                                     @Param("keyword") String keyword,
                                     @Param("artistId") Long artistId,
                                     @Param("minSongs") Integer minSongs,
                                     @Param("maxSongs") Integer maxSongs);

    List<Long> findAlbumIdsByKeyword(@Param("keyword") String keyword,
                                      @Param("letter") String letter,
                                      @Param("artistId") Long artistId,
                                      @Param("minSongs") Integer minSongs,
                                      @Param("maxSongs") Integer maxSongs);

    List<Long> findIncompleteAlbumIds(@Param("letter") String letter,
                                       @Param("keyword") String keyword,
                                       @Param("artistId") Long artistId,
                                       @Param("minSongs") Integer minSongs,
                                       @Param("maxSongs") Integer maxSongs);

    List<Long> findUnenrichedAlbumIds(@Param("letter") String letter,
                                       @Param("keyword") String keyword,
                                       @Param("artistId") Long artistId,
                                       @Param("minSongs") Integer minSongs,
                                       @Param("maxSongs") Integer maxSongs);

    List<Long> findNakedAlbumIds(@Param("letter") String letter,
                                  @Param("keyword") String keyword,
                                  @Param("artistId") Long artistId,
                                  @Param("minSongs") Integer minSongs,
                                  @Param("maxSongs") Integer maxSongs);

    List<Long> findDuplicateAlbumIds(@Param("letter") String letter,
                                      @Param("keyword") String keyword,
                                      @Param("artistId") Long artistId,
                                      @Param("minSongs") Integer minSongs,
                                      @Param("maxSongs") Integer maxSongs);
}
