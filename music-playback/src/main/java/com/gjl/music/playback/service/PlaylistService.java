package com.gjl.music.playback.service;

import com.gjl.music.model.Song;
import com.gjl.music.playback.model.Playlist;

import java.util.List;
import java.util.Map;

/**
 * 播放列表服务。
 */
public interface PlaylistService {

    /** 列出用户的所有播放列表（按用户名） */
    List<Playlist> listByUsername(String username);

    /** 列出用户的所有播放列表（按用户 ID） */
    List<Playlist> listByUser(Long userId);

    /** 获取播放列表详情（含歌曲列表） */
    Playlist getById(Long id);

    /** 获取播放列表中的歌曲 */
    List<Song> getSongs(Long playlistId);

    /** 获取播放列表前 N 首歌曲（用于封面拼贴） */
    List<Song> getFirstSongs(Long playlistId, int limit);

    /** 批量获取多个歌单的前 N 首歌曲，按 playlistId 分组 */
    Map<Long, List<Song>> getFirstSongsBatch(List<Long> playlistIds, int limit);

    /** 创建播放列表 */
    Playlist create(String name, String comment, Long ownerId, boolean isPublic, String coverPath);

    /** 按用户名 + userId 创建播放列表 */
    Playlist createByUsername(String name, String comment, String username,
                              Long userId, boolean isPublic);

    /** 按用户名 + userId 创建播放列表（含封面路径） */
    Playlist createByUsername(String name, String comment, String username,
                              Long userId, boolean isPublic, String coverPath);

    /** 更新播放列表元数据 */
    Playlist update(Long id, String name, String comment, boolean isPublic, String coverPath);

    /** 删除播放列表 */
    void delete(Long id);

    /** 向播放列表添加歌曲（可多首） */
    void addSongs(Long playlistId, List<Long> songIds);

    /** 从播放列表中移除指定位置的歌曲 */
    void removeSong(Long playlistId, int position);

    /** 导出为 M3U8 格式 */
    String exportM3u(Long playlistId);

    /** 从 M3U 文件导入（返回新增的歌曲 ID 列表） */
    List<Long> importM3u(Long playlistId, String m3uContent, String rootDir);

    /** 调整歌曲在播放列表中的位置（大间隔插入算法，Subsonic 兼容） */
    void reorderSong(Long playlistId, int fromPosition, int toPosition);

    /** 全量替换播放列表歌曲顺序（前端排序后一次落盘） */
    void reorderAll(Long playlistId, List<Long> songIds);

    /** 查询歌曲所属的歌单列表（仅返回当前用户拥有的 + 公开的歌单） */
    List<Playlist> getPlaylistsContainingSong(String username, Long songId);

    /** 上传歌单封面图，返回存储路径 */
    String uploadCover(Long playlistId, org.springframework.web.multipart.MultipartFile file)
            throws java.io.IOException;
}
