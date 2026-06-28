package com.gjl.music.module.song.formatconvert;

import com.gjl.music.module.Module;

import java.util.Set;

/**
 * 格式转换模块接口 —— 将音频文件转换为指定格式（MP3/FLAC/WAV/AAC/OGG/OPUS/ALAC/AIFF/WMA）。
 *
 * <p>支持 Pipeline 管道模式，通过 {@code processItem} 处理单个文件。
 * 转换后文件放在同目录下，元数据标签完整保留。</p>
 */
public interface FormatConvertModule extends Module {

    /** 目标格式配置键 */
    String KEY_TARGET_FORMAT = "targetFormat";

    /** 比特率配置键 (kbps)，0 表示使用默认值，无损格式忽略 */
    String KEY_BITRATE = "bitrate";

    /** 采样率配置键 (Hz)，0 表示保持原始 */
    String KEY_SAMPLE_RATE = "sampleRate";

    /** 声道数配置键，0 表示保持原始 */
    String KEY_CHANNELS = "channels";

    /** 是否删除原始文件 */
    String KEY_DELETE_ORIGINAL = "deleteOriginal";

    /** 默认目标格式 */
    String DEFAULT_FORMAT = "mp3";

    /** 默认比特率 (kbps) */
    int DEFAULT_BITRATE = 320;

    /** 默认采样率：0 = 保持原始 */
    int DEFAULT_SAMPLE_RATE = 0;

    /** 默认声道数：0 = 保持原始 */
    int DEFAULT_CHANNELS = 0;

    /** 默认不删除原文件 */
    boolean DEFAULT_DELETE_ORIGINAL = false;

    /** 支持的目标格式 */
    Set<String> SUPPORTED_FORMATS = Set.of("mp3", "flac", "wav", "aac", "ogg", "opus", "alac", "aiff", "wma");

    /** 有损压缩格式 */
    Set<String> LOSSY_FORMATS = Set.of("mp3", "aac", "ogg", "opus", "wma");

    /** 无损格式 */
    Set<String> LOSSLESS_FORMATS = Set.of("flac", "wav", "alac", "aiff");
}
