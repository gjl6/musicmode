package com.gjl.music.editor.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * Song 管理操作 Mapper（仅 editor 使用）。
 * 包含去重、文件整理、批量删除、缓存辅助查询等 Pipeline/管理功能。
 */
@Mapper
public interface SongManageMapper {

    // ── 批量删除 ──

    void deleteSongArtistsBySongIds(@Param("songIds") List<Long> songIds);

    void deleteSongStylesBySongIds(@Param("songIds") List<Long> songIds);

    void deleteLyricsBySongIds(@Param("songIds") List<Long> songIds);

    void deleteSongsByIds(@Param("songIds") List<Long> songIds);

    // ── 文件整理 ──

    /** 更新歌曲文件路径（用于文件移动后同步 DB） */
    void updateSongFilePath(@Param("oldPath") String oldPath,
                            @Param("newPath") String newPath,
                            @Param("newFileName") String newFileName);

    // ── DB 同步辅助 ──

    /** 查询指定根路径下所有歌曲的 id 和 file_path */
    List<Map<String, Object>> selectSongPathsUnderRoot(@Param("rootPath") String rootPath);

    /** 按路径列表批量查询 file_path, id, file_mtime（用于 FILE_SCAN 分批比对） */
    List<Map<String, Object>> selectSongPathsByList(@Param("paths") List<String> paths);

    /** 分页查询指定根路径下的歌曲 id 和 file_path（用于流式删除检测） */
    List<Map<String, Object>> selectSongIdsUnderRootPaged(@Param("rootPath") String rootPath,
                                                          @Param("offset") int offset,
                                                          @Param("limit") int limit);

    /** 按多个目录前缀查询歌曲 id 和 file_path */
    List<Map<String, Object>> selectSongPathsUnderDirs(@Param("dirs") List<String> dirs);

    // ── 去重查询 ──

    /** 按文件哈希查找重复文件组（仅返回 count > 1 的组） */
    List<Map<String, Object>> findHashDuplicates(@Param("rootPath") String rootPath);

    /** 按文件名查找重复文件组（仅返回 count > 1 的组） */
    List<Map<String, Object>> findFileNameDuplicates(@Param("rootPaths") List<String> rootPaths);

    /** 获取去重所需的元数据字段（title, duration, album, artist） */
    List<Map<String, Object>> findMetadataForDedup(@Param("rootPaths") List<String> rootPaths);

    /** 获取去重所需的指纹和时长 */
    List<Map<String, Object>> findFingerprintsForDedup(@Param("rootPath") String rootPath);

    /** 获取 rootPaths 下所有 song 的关键字段（含 NULL），供 dedup 策略按需补缺 */
    List<Map<String, Object>> findAllSongsUnderRoot(@Param("rootPaths") List<String> rootPaths);

    // ── 批量回填 ──

    /** 批量回填 file_hash（CASE WHEN 单条 UPDATE） */
    void batchUpdateSongHash(@Param("list") List<Map<String, Object>> list);

    /** 批量回填 fingerprint（CASE WHEN 单条 UPDATE） */
    void batchUpdateSongFingerprint(@Param("list") List<Map<String, Object>> list);

    // ── 缓存辅助查询 ──

    /** 根据指定路径列表批量查询指纹（用于去重候选精准比对） */
    List<Map<String, Object>> findFingerprintsByPaths(@Param("paths") List<String> paths);

    /** 根据指定路径列表批量查询元数据字段（缓存命中） */
    List<Map<String, Object>> findMetadataByPaths(@Param("paths") List<String> paths);

    /** 根据路径列表查询 song + album + artist + style + lyric 全部字段（用于重建完整 MusicMetadata） */
    List<Map<String, Object>> findFullMetadataByPaths(@Param("paths") List<String> paths);
}
