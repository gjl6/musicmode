package com.gjl.music.service.song;

import com.gjl.music.model.MusicMetadata;

import java.util.Map;

/**
 * 单文件元数据编辑服务接口 —— 接收前端结构化元数据写回音频文件。
 */
public interface MusicEditService {

    /**
     * 保存前端传来的结构化元数据到音频文件。
     * @param rawPath URL 编码的文件相对路径
     * @param metaMap 前端结构化元数据 Map
     * @return 写入的元数据
     */
    MusicMetadata saveFields(String rawPath, Map<String, Object> metaMap);

    /** 下载封面图片到本地并返回绝对路径 */
    String downloadCover(String url);
}
