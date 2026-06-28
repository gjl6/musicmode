package com.gjl.music.module.song.parser;

import com.gjl.music.module.Module;
import com.gjl.music.parser.ParserFactory;

/**
 * 音频文件解析模块接口 —— 将音频文件解析为 MusicMetadata。
 *
 * <p>支持 Pipeline 流式和批量模式。解析工厂契约由 {@link ParserFactory} 定义。</p>
 */
public interface ParserModule extends Module {

    /** 获取内部解析器工厂，允许外部直接调用解析能力 */
    ParserFactory getParserFactory();
}
