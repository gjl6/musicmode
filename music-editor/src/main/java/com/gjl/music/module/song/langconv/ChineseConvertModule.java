package com.gjl.music.module.song.langconv;

import com.gjl.music.model.MusicMetadata;
import com.gjl.music.module.Module;

/**
 * 繁简转换模块接口 —— 将元数据中的繁体中文转为简体中文。
 *
 * <p>支持 Pipeline 流式和批量模式，同时对外提供直接调用能力。</p>
 */
public interface ChineseConvertModule extends Module {

    /** 将繁体中文文本转为简体中文 */
    String toSimplified(String text);

    /** 将简体中文文本转为繁体中文 */
    String toTraditional(String text);

    /** 转换整份 MusicMetadata 中的所有文本字段 */
    MusicMetadata convert(MusicMetadata meta);
}
