package com.gjl.music.service.song;

/**
 * 标签同步服务接口 —— 元数据变更后将 DB 变更同步回音频文件标签。
 */
public interface TagSyncService {

    /** 专辑元数据变更 → 同步该专辑下所有歌曲的文件标签 */
    void syncAlbumChange(Long albumId);

    /** 艺术家元数据变更 → 同步该艺术家所有歌曲的文件标签 */
    void syncArtistChange(Long artistId);

    /** 风格元数据变更 → 同步该风格所有歌曲的文件标签 */
    void syncStyleChange(Long styleId);
}
