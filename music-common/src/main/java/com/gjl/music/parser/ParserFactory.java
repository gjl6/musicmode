package com.gjl.music.parser;

import com.gjl.music.exception.MetadataParseException;
import com.gjl.music.model.MusicMetadata;

import java.io.File;

/**
 * 解析器工厂接口 —— 根据文件创建合适的解析器并解析元数据。
 */
public interface ParserFactory {

    /**
     * 解析音频文件，提取为统一的 MusicMetadata 模型。
     *
     * @param file 音频文件
     * @return 解析后的元数据
     * @throws MetadataParseException 解析失败时抛出
     */
    MusicMetadata parse(File file) throws MetadataParseException;

    /**
     * 轻量解析 —— 跳过 SHA-256 文件哈希和 Files.readAttributes() 调用。
     * 用于目录浏览等不需要文件哈希的场景，在 Docker/网络文件系统下显著减少 I/O。
     *
     * @param file 音频文件
     * @return 解析后的元数据（fileHash/createTime/updateTime 为 null）
     * @throws MetadataParseException 解析失败时抛出
     */
    MusicMetadata parseLight(File file) throws MetadataParseException;
}
