package com.gjl.music.playback.mapper;

import com.gjl.music.model.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 浏览/查询 Mapper（仅 music-playback 使用）。
 * 包含艺术家、专辑、歌曲、流派的浏览查询和辅助查询方法。
 * 从 MusicMapper 拆分出来，遵循模块自管原则。
 */
@Mapper
public interface BrowseMapper {

    // ── 艺术家浏览 ──

    /** 分页查询艺术家（支持按首字母过滤 + 排序）。sort: alphabetical(默认)/albumCount/songCount */
    List<Artist> findArtists(@Param("letter") String letter, @Param("sort") String sort,
                             @Param("offset") int offset, @Param("limit") int limit);

    /** 艺术家总数（可按字母过滤） */
    int countArtists(@Param("letter") String letter);

    /** 统计各首字母的艺术家数量（A-Z、#） */
    List<Map<String, Object>> getArtistLetters();

    /** 按首字母分组查询艺术家索引 */
    List<Map<String, Object>> findArtistIndex();

    /** 获取所有艺术家简化字段（id + name + albumCount + songCount），按名称排序。
     *  用于 Subsonic getIndexes，一次查询替代 N+1 */
    List<Artist> findAllArtistsSimple();

    // ── 专辑浏览 ──

    /** 按类型分页查询专辑。type: newest/alphabetical/random/byYear/recent/frequent/mostPlayed。
     *  letter 可选首字母过滤，starred 可选仅收藏，artistId 可选按艺术家过滤 */
    List<Album> findAlbumsByType(@Param("type") String type, @Param("offset") int offset,
                                  @Param("limit") int limit, @Param("userId") Long userId,
                                  @Param("letter") String letter, @Param("starred") Boolean starred,
                                  @Param("starredUsername") String starredUsername,
                                  @Param("artistId") Long artistId);

    /** 专辑总数（可按字母过滤，starred 仅收藏，artistId 按艺术家过滤） */
    int countAlbumsByLetter(@Param("letter") String letter, @Param("starred") Boolean starred,
                            @Param("starredUsername") String starredUsername,
                            @Param("artistId") Long artistId);

    /** 统计各首字母的专辑数量（A-Z、0-9、#）。starred 可选仅收藏 */
    List<Map<String, Object>> getAlbumLetters(@Param("starred") Boolean starred,
                                              @Param("starredUsername") String starredUsername);

    // ── 歌曲浏览 ──

    /** 分页查询全部歌曲（按字母过滤，支持排序：newest/alphabetical/frequent）。artistId 可选按艺术家过滤 */
    List<Song> findSongsPaginated(@Param("offset") int offset, @Param("limit") int limit,
                                   @Param("letter") String letter, @Param("sort") String sort,
                                   @Param("userId") Long userId, @Param("artistId") Long artistId);

    /** 歌曲总数（可按字母过滤，artistId 可选） */
    int countSongs(@Param("letter") String letter, @Param("artistId") Long artistId);

    /** 统计各首字母的歌曲数量（A-Z、0-9、#） */
    List<Map<String, Object>> getSongLetters();

    /** 随机获取指定数量的歌曲 */
    List<Song> findRandomSongs(@Param("limit") int limit);

    // ── 流派浏览 ──

    /** 查询所有风格及其歌曲数量（兼容 Subsonic getGenres） */
    List<Map<String, Object>> findDistinctGenres();

    /** 分页查询风格列表，支持首字母过滤 + 排序（name|songCount） */
    List<Map<String, Object>> findGenresPaginated(@Param("letter") String letter,
                                                   @Param("sort") String sort,
                                                   @Param("offset") int offset,
                                                   @Param("limit") int limit);

    /** 风格总数（可按首字母过滤） */
    int countGenres(@Param("letter") String letter);

    /** 统计各首字母的风格数量 */
    List<Map<String, Object>> getGenreLetters();

    /** 按风格名称查询歌曲（支持字母过滤 + 排序 + 分页） */
    List<Song> findSongsByGenre(@Param("genre") String genre,
                                @Param("letter") String letter,
                                @Param("sort") String sort,
                                @Param("offset") int offset,
                                @Param("limit") int limit,
                                @Param("userId") Long userId);

    /** 按风格查询歌曲总数（支持字母过滤） */
    int countSongsByGenre(@Param("genre") String genre, @Param("letter") String letter);

    // ── 辅助查询 ──

    /** 根据歌曲 ID 查询所有关联的艺术家 ID（通过 song_artist 表） */
    List<Long> findArtistIdsBySongId(@Param("songId") Long songId);

    /** 根据歌曲 ID 查询所有关联的风格 ID（通过 song_style 表） */
    List<Long> findStyleIdsBySongId(@Param("songId") Long songId);

    /** 根据风格 ID 查询所有关联的歌曲 ID（通过 song_style 表） */
    List<Long> findSongIdsByStyleId(@Param("styleId") Long styleId);

    /** 根据用户名查询用户 ID（auth_user 表） */
    Long findUserIdByUsername(@Param("username") String username);

    /** 查找与指定艺术家共享流派的相似艺术家（按共享流派数降序排列）。
     *  用于 Subsonic getArtistInfo2 的 similarArtist 字段 */
    List<Artist> findSimilarArtists(@Param("artistId") Long artistId,
                                    @Param("limit") int limit);

    /** DB 关键字搜索歌曲（Lucene 索引为空时的回退方案） */
    List<Song> searchSongsByKeyword(@Param("keyword") String keyword,
                                    @Param("offset") int offset,
                                    @Param("limit") int limit);

    /** DB 关键字搜索专辑（Lucene 索引为空时的回退方案） */
    List<Album> searchAlbumsByKeyword(@Param("keyword") String keyword,
                                      @Param("offset") int offset,
                                      @Param("limit") int limit);

    /** DB 关键字搜索艺术家（Lucene 索引为空时的回退方案） */
    List<Artist> searchArtistsByKeyword(@Param("keyword") String keyword,
                                        @Param("offset") int offset,
                                        @Param("limit") int limit);
}
