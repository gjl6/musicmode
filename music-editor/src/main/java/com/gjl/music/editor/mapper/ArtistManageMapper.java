package com.gjl.music.editor.mapper;

import com.gjl.music.model.Artist;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * Artist 管理操作 Mapper（仅 editor 使用）。
 * 包含艺术家搜索、重复检测、合并操作、Pipeline 扫描查询。
 */
@Mapper
public interface ArtistManageMapper {

    // ── 搜索 ──

    List<Artist> searchArtists(@Param("keyword") String keyword,
                               @Param("letter") String letter,
                               @Param("sort") String sort,
                               @Param("offset") int offset,
                               @Param("limit") int limit);

    int countSearchArtists(@Param("keyword") String keyword,
                           @Param("letter") String letter);

    // ── 重复检测 + 合并 ──

    /** 查找大小写重复的艺术家名称组（按 LOWER(artist_name) 分组，cnt > 1） */
    List<Map<String, Object>> findDuplicateArtistNames();

    /** 按归一化名称查询艺术家（LOWER(artist_name) = LOWER(#{normName})） */
    List<Artist> findArtistsByNormalizedName(@Param("normName") String normName);

    /** 将 source 艺术家的歌曲重分配到 target（跳过已关联 target 的歌曲） */
    int reassignSongArtists(@Param("sourceId") Long sourceId,
                            @Param("targetId") Long targetId);

    /** 删除 source 艺术家的歌曲关联（仅删除该歌曲已同时关联 target 的记录） */
    int deleteConflictingSongArtists(@Param("sourceId") Long sourceId,
                                      @Param("targetId") Long targetId);

    /** 将专辑的艺术家从 source 改为 target */
    int reassignAlbumArtist(@Param("sourceId") Long sourceId,
                            @Param("targetId") Long targetId);

    /** 删除艺术家 */
    int deleteArtist(@Param("id") Long id);

    // ── Pipeline 扫描查询 ──

    List<Long> findAllArtistIds(@Param("letter") String letter,
                                @Param("keyword") String keyword,
                                @Param("minSongs") Integer minSongs,
                                @Param("maxSongs") Integer maxSongs,
                                @Param("style") String style,
                                @Param("country") String country);

    List<Long> findArtistIdsByLetter(@Param("letter") String letter,
                                     @Param("keyword") String keyword,
                                     @Param("minSongs") Integer minSongs,
                                     @Param("maxSongs") Integer maxSongs,
                                     @Param("style") String style,
                                     @Param("country") String country);

    List<Long> findArtistIdsByKeyword(@Param("keyword") String keyword,
                                      @Param("letter") String letter,
                                      @Param("minSongs") Integer minSongs,
                                      @Param("maxSongs") Integer maxSongs,
                                      @Param("style") String style,
                                      @Param("country") String country);

    List<Long> findIncompleteArtistIds(@Param("letter") String letter,
                                       @Param("keyword") String keyword,
                                       @Param("minSongs") Integer minSongs,
                                       @Param("maxSongs") Integer maxSongs,
                                       @Param("style") String style,
                                       @Param("country") String country);

    List<Long> findUnenrichedArtistIds(@Param("letter") String letter,
                                       @Param("keyword") String keyword,
                                       @Param("minSongs") Integer minSongs,
                                       @Param("maxSongs") Integer maxSongs,
                                       @Param("style") String style,
                                       @Param("country") String country);

    List<Long> findNonstandardArtistIds(@Param("letter") String letter,
                                        @Param("keyword") String keyword,
                                        @Param("minSongs") Integer minSongs,
                                        @Param("maxSongs") Integer maxSongs,
                                        @Param("style") String style,
                                        @Param("country") String country);

    List<Long> findNakedArtistIds(@Param("letter") String letter,
                                  @Param("keyword") String keyword,
                                  @Param("minSongs") Integer minSongs,
                                  @Param("maxSongs") Integer maxSongs,
                                  @Param("style") String style,
                                  @Param("country") String country);

    List<Long> findDuplicateArtistIds(@Param("letter") String letter,
                                      @Param("keyword") String keyword,
                                      @Param("minSongs") Integer minSongs,
                                      @Param("maxSongs") Integer maxSongs,
                                      @Param("style") String style,
                                      @Param("country") String country);
}
