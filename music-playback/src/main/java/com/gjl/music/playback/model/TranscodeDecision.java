package com.gjl.music.playback.model;

/**
 * 转码决策结果。
 *
 * @param canDirectPlay    是否可直接播放（无需转码）
 * @param targetFormat     目标格式（如 "mp3", "opus", "aac", "flac"）
 * @param targetBitrate    目标比特率（kbps），无损为 0
 * @param targetSampleRate 目标采样率（Hz），0 表示不限制
 * @param targetChannels   目标声道数，0 表示不限制
 * @param targetMimeType   目标 MIME Type
 * @param commandName      使用的转码命令名（如 "mp3 audio"）
 */
public record TranscodeDecision(
        boolean canDirectPlay,
        String targetFormat,
        int targetBitrate,
        int targetSampleRate,
        int targetChannels,
        String targetMimeType,
        String commandName
) {
    /** 创建"直接播放"决策 */
    public static TranscodeDecision directPlay(String sourceFormat, String mimeType) {
        return new TranscodeDecision(true, sourceFormat, 0, 0, 0, mimeType, null);
    }

    /** 创建"需要转码"决策 */
    public static TranscodeDecision transcode(String targetFormat, int targetBitrate,
                                              int targetSampleRate, int targetChannels,
                                              String targetMimeType, String commandName) {
        return new TranscodeDecision(false, targetFormat, targetBitrate,
                targetSampleRate, targetChannels, targetMimeType, commandName);
    }
}
