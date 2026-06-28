package com.gjl.music.service.song;

import com.gjl.music.model.MusicMetadata;

import java.util.List;

/**
 * 音乐元数据增强服务接口 —— Controller 与 enrich 模块之间的桥梁。
 */
public interface EnrichService {

    /**
     * 使用指定 provider 搜索并增强音乐元数据。
     * @param providerName 如 "qqmusic", "kugou", "all"
     * @param meta         查询元数据
     * @return 按相关性降序排列的结果列表
     */
    List<MusicMetadata> search(String providerName, MusicMetadata meta);
}
