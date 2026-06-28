package com.gjl.music.module.song.cuesplit;

import com.gjl.music.module.Module;

/**
 * 音轨分割模块 —— 使用 CUE 文件将整轨音频分割为独立音轨。
 *
 * <p>配置项（通过 configure 传入）：
 * <ul>
 *   <li>{@code outputFormat} — "keep"（保持源格式，默认）、"flac"、"mp3"</li>
 *   <li>{@code deleteSource} — 分割后是否删除整轨源文件（默认 false）</li>
 *   <li>{@code writeTags}   — 是否写入 ID3 标签（默认 true）</li>
 * </ul>
 */
public interface CueSplitModule extends Module {

    String KEY_OUTPUT_FORMAT = "outputFormat";
    String KEY_DELETE_SOURCE = "deleteSource";
    String KEY_WRITE_TAGS   = "writeTags";

    String DEFAULT_OUTPUT_FORMAT = "keep";
    boolean DEFAULT_DELETE_SOURCE = false;
    boolean DEFAULT_WRITE_TAGS = true;
}
