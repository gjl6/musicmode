package com.gjl.music.module.song.writer;

import com.gjl.music.exception.MetadataWriteException;
import com.gjl.music.model.MusicMetadata;

import java.io.File;

/**
 * 写入器工厂接口 —— 将元数据写回音频文件。
 */
public interface WriterFactory {

    /**
     * 将 MusicMetadata 写回到指定的音频文件中。
     *
     * @param file     目标音频文件
     * @param metadata 要写入的元数据
     * @throws MetadataWriteException 写入失败时抛出
     */
    void write(File file, MusicMetadata metadata) throws MetadataWriteException;
}
