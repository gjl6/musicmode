package com.gjl.music.playback.infra.transcoding;

import com.gjl.music.model.Song;
import com.gjl.music.playback.model.TranscodeDecision;

/**
 * 转码决策引擎 —— 判断是否需要转码以及如何转码。
 *
 * <p>参照 Navidrome {@code TranscodeDecider.MakeDecision()} 的决策流程。
 */
public interface TranscodeDecider {

    /**
     * 根据源文件和请求参数决定是否需要转码。
     *
     * @param song       歌曲记录（含 filePath, bitrate, sampleRate, channels, fileFormat）
     * @param reqFormat  客户端请求的目标格式（null/empty/raw = 原始）
     * @param maxBitRate 客户端最大比特率限制（0 = 不限）
     * @return 转码决策
     */
    TranscodeDecision decide(Song song, String reqFormat, int maxBitRate);
}
