package com.gjl.music.watch;

import java.util.List;
import java.util.Map;

/**
 * 歌曲数据库访问接口 — 扫描器通过此接口查询和操作歌曲表。
 *
 * <p>实现示例：
 * <ul>
 *   <li>editor: {@code SongManageMapper} 适配</li>
 *   <li>playback: {@code SongMapper} 适配（或更简单的实现）</li>
 * </ul>
 */
public interface SongDbAccessor {

    /**
     * 按绝对路径列表批量查询。
     *
     * @param paths 文件绝对路径列表
     * @return absPath → [songId, fileMtime] 的映射
     */
    Map<String, long[]> queryByPaths(List<String> paths);

    /**
     * 按根路径前缀查询所有歌曲路径及 mtime。
     *
     * @param rootPath 根路径前缀（平台分隔符）
     * @return absPath → [songId, fileMtime] 的映射
     */
    Map<String, long[]> queryUnderRoot(String rootPath);

    /**
     * 分页查询根路径下的歌曲 ID 和路径（删除检测用）。
     *
     * @param rootPath 根路径前缀
     * @param offset   偏移量
     * @param limit    每页大小
     * @return 包含 ID 和 FILE_PATH 的行列表
     */
    List<Map<String, Object>> queryPaged(String rootPath, int offset, int limit);

    /**
     * 级联删除指定歌曲（song_artists → song_styles → lyrics → songs）。
     */
    void cascadeDelete(List<Long> songIds);
}
