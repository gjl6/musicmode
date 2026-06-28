package com.gjl.music.service.song;

import com.gjl.music.model.MusicMetadata;

import java.util.Map;

/**
 * 文件浏览服务接口 —— 目录浏览、元数据读取。
 */
public interface BrowseService {

    /** 浏览目录：子文件夹 + 音频文件基本属性（无标签解析，快） */
    Map<String, Object> listDirectory(String rawPath);

    /** 单文件完整元数据，直接返回 MusicMetadata */
    MusicMetadata getMetadata(String rawPath);

    /** 批量目录元数据（分页），只解析当前页文件的标签 */
    Map<String, Object> getDirectoryMetadata(String rawPath, int page, int pageSize);
}
