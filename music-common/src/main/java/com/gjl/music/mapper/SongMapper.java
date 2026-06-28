package com.gjl.music.mapper;

import com.gjl.music.model.Song;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface SongMapper {

    // ── 写操作 ──

    void upsertSong(@Param("song") Song song, @Param("albumId") Long albumId);

    Long selectSongIdByFilePath(String filePath);

    void batchUpsertSongs(@Param("list") List<Song> list);

    List<Map<String, Object>> selectSongIdsByFilePaths(@Param("paths") List<String> paths);

    // ── 关联写入 ──

    void insertSongArtist(@Param("songId") Long songId, @Param("artistId") Long artistId, @Param("sortOrder") int sortOrder);

    void insertSongStyle(@Param("songId") Long songId, @Param("styleId") Long styleId);

    void batchInsertSongArtists(@Param("list") List<Map<String, Object>> list);

    void batchInsertSongStyles(@Param("list") List<Map<String, Object>> list);

    // ── 核心读查询（editor + playback 共享）──

    /** 根据 ID 查询单首歌曲（含关联的 album + artist 信息） */
    Song findSongById(@Param("id") Long id);

    /** 根据文件路径查询歌曲完整信息 */
    Song findSongByFilePath(@Param("filePath") String filePath);

    /** 根据 ID 列表批量查询歌曲 */
    List<Song> findSongsByIds(@Param("ids") List<Long> ids);

    /** 按专辑 ID 批量查询歌曲（按曲目号排序） */
    List<Song> findSongsByAlbumIds(@Param("albumIds") List<Long> albumIds);

    /** 按专辑 ID 查询歌曲（按曲目号排序） */
    List<Song> findSongsByAlbumId(@Param("albumId") Long albumId);

    /** 按艺术家 ID 查询歌曲（通过 song_artist 关联，按专辑+曲目号排序） */
    List<Song> findSongsByArtistId(@Param("artistId") Long artistId);

    /** 查询全部歌曲 ID（用于 Lucene 全量索引构建） */
    List<Long> findAllSongIds();

    /** 统计歌曲总数（用于索引健康检查） */
    int countAllSongs();

    /** 查询指定时间之后更新的歌曲（用于 Lucene 增量索引） */
    List<Long> findSongIdsUpdatedAfter(@Param("since") java.time.LocalDateTime since);

    // ── 关联查询（播放/搜索使用）──

    /** 根据歌曲 ID 查询第一个艺术家 ID（通过 song_artist 关联表） */
    Integer findFirstArtistIdBySongId(@Param("songId") Long songId);

    /** 根据歌曲 ID 查询风格名称（通过 song_style + style 关联表，取第一个） */
    String findFirstGenreBySongId(@Param("songId") Long songId);

    /** 批量查询歌曲风格，返回 [{songId, styleName}, ...]，每首歌取第一个风格 */
    List<Map<String, Object>> findGenresBySongIds(@Param("songIds") List<Long> songIds);

    /** 批量查询歌曲的所有艺术家（song_artist + artist JOIN），返回 [{songId, artistId, sortOrder, artistName}, ...]，按 sort_order 排序 */
    List<Map<String, Object>> findSongArtistsBySongIds(@Param("songIds") List<Long> songIds);

    // ── 封面 fallback 查询 ──

    /** 查专辑下第一首有封面的歌曲的 cover_path */
    String findFirstSongCoverByAlbumId(@Param("albumId") Long albumId);

    /** 查艺术家下第一张有封面的专辑的 album_cover */
    String findFirstAlbumCoverByArtistId(@Param("artistId") Long artistId);

    /** 查艺术家关联的第一首有封面的歌曲的 cover_path（通过 song_artist 表关联，不限 sort_order） */
    String findFirstSongCoverByArtistId(@Param("artistId") Long artistId);

    // ── 冗余计数刷新（在写入路径调用，保持 album/artist 计数准确）──

    /** 根据歌曲 ID 批量查询关联的专辑 ID（去重） */
    List<Long> findAlbumIdsBySongIds(@Param("songIds") List<Long> songIds);

    /** 根据歌曲 ID 批量查询关联的艺术家 ID（去重） */
    List<Long> findArtistIdsBySongIds(@Param("songIds") List<Long> songIds);

    /** 根据歌曲 ID 批量查询关联的风格 ID（去重） */
    List<Long> findStyleIdsBySongIds(@Param("songIds") List<Long> songIds);

    /** 批量更新专辑歌曲计数 */
    void updateAlbumSongCounts(@Param("albumIds") List<Long> albumIds);

    /** 批量更新艺术家专辑计数 */
    void updateArtistAlbumCounts(@Param("artistIds") List<Long> artistIds);

    /** 批量更新艺术家歌曲计数 */
    void updateArtistSongCounts(@Param("artistIds") List<Long> artistIds);

    /** 批量更新风格歌曲计数 */
    void updateStyleSongCounts(@Param("styleIds") List<Long> styleIds);

    /** 根据风格 ID 查询关联的歌曲 ID */
    List<Long> findSongIdsByStyleId(@Param("styleId") Long styleId);

    /** 按文件路径前缀查找歌曲（用于 Subsonic getMusicDirectory 目录浏览模式） */
    List<Song> findSongsByPathPrefix(@Param("prefix") String pathPrefix);

    /** 查指定专辑的第一个风格名称（通过 song_style + style 表）。
     *  用于 Subsonic getAlbum 响应的 genre 字段 */
    String findFirstGenreByAlbumId(@Param("albumId") Long albumId);
}
