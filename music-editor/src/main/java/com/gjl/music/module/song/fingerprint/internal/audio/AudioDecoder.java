package com.gjl.music.module.song.fingerprint.internal.audio;

import java.nio.file.Path;

/** 音频解码器接口 —— 将音频文件解码为 PCM 浮点样本 */
public interface AudioDecoder {

    /** 解码结果 */
    record DecodedAudio(float[] samples, int sampleRate, int channels, int durationSeconds) {}

    /** 解码音频文件 */
    DecodedAudio decode(Path audioFile) throws AudioDecodeException;

    /** 是否支持该文件格式 */
    boolean supports(Path audioFile);
}
