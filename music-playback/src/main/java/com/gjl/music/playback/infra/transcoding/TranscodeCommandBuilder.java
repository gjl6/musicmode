package com.gjl.music.playback.infra.transcoding;

import com.gjl.music.config.ConfigService;
import com.gjl.music.infra.util.FfmpegUtil;
import com.gjl.music.playback.model.TranscodeDecision;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * FFmpeg 转码命令行构建器。
 *
 * <p>薄封装 {@link FfmpegUtil}，构建转码命令。
 * FfmpegUtil 提供进程管理（启动、pipe、自动销毁）、格式映射、ffprobe 探测。
 * 本类只负责将 {@link TranscodeDecision} 转换为具体的命令行参数。
 */
@Component
public class TranscodeCommandBuilder {

    private final String ffmpegPath;

    public TranscodeCommandBuilder(ConfigService configService) {
        this.ffmpegPath = configService.getString("music.ffmpeg.path", "ffmpeg");
    }

    /**
     * 构建 FFmpeg 转码命令行。
     *
     * @param sourceFile 源音频文件路径
     * @param decision   转码决策
     * @param offsetSec  时间偏移（秒），0 表示从头开始
     * @return 完整命令行（含 ffmpeg 路径和所有参数）
     */
    public List<String> build(Path sourceFile, TranscodeDecision decision, int offsetSec) {
        List<String> cmd = FfmpegUtil.commandPrefix(ffmpegPath);

        // 时间偏移
        if (offsetSec > 0) {
            cmd.addAll(List.of("-ss", String.valueOf(offsetSec)));
        }

        // 输入文件
        cmd.addAll(List.of("-i", sourceFile.toString()));
        cmd.addAll(List.of("-map", "0:a:0"));

        // 编码器
        cmd.addAll(List.of("-c:a", FfmpegUtil.codecForFormat(decision.targetFormat())));

        // 比特率
        int br = decision.targetBitrate();
        if (br > 0) {
            cmd.addAll(List.of("-b:a", br + "k"));
        }

        // 采样率
        if (decision.targetSampleRate() > 0) {
            cmd.addAll(List.of("-ar", String.valueOf(decision.targetSampleRate())));
        }

        // 声道数
        if (decision.targetChannels() > 0) {
            cmd.addAll(List.of("-ac", String.valueOf(decision.targetChannels())));
        }

        // 容器格式 → stdout
        cmd.addAll(List.of("-f", FfmpegUtil.containerForFormat(decision.targetFormat())));
        cmd.add("pipe:1");

        return cmd;
    }
}
