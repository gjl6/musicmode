package com.gjl.music.module.song.encoding;

import com.gjl.music.model.MusicMetadata;
import com.gjl.music.module.Module;

import java.util.Set;

/**
 * 乱码修复模块接口 —— 检测并修复因编码转换导致的元数据乱码。
 *
 * <p>支持 Pipeline 流式和批量模式，同时对外提供直接调用能力。</p>
 */
public interface EncodingRepairModule extends Module {

    /** 修复单段文本的编码乱码，正常文本原样返回 */
    String repair(String text);

    /** 按指定字段集合修复整份 MusicMetadata，只处理 fields 中包含的字段 */
    MusicMetadata repairMetadata(MusicMetadata meta, Set<String> fields);
}
