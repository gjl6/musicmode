package com.gjl.music.module.song.writer;

import com.gjl.music.module.Module;

/**
 * 元数据写入模块接口 —— 将处理完成的 MusicMetadata 写回音频文件。
 *
 * <p>支持 Pipeline 流式和批量模式。写入能力由 {@link WriterFactory} 提供。</p>
 */
public interface WriterModule extends Module {
}
