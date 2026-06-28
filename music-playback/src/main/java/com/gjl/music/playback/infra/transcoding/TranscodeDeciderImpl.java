package com.gjl.music.playback.infra.transcoding;

import com.gjl.music.infra.util.FfmpegUtil;
import com.gjl.music.model.Song;
import com.gjl.music.playback.config.PlaybackProperties;
import com.gjl.music.playback.model.TranscodeDecision;
import com.gjl.music.infra.util.ContentTypeResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 转码决策引擎实现。
 *
 * <h3>决策流程（参照 Navidrome MakeDecision）</h3>
 * <ol>
 *   <li>客户端未请求特定格式/比特率 → 直接播放</li>
 *   <li>源格式 = 目标格式 且 比特率满足约束 → 直接播放</li>
 *   <li>有损→无损拒绝（MP3→FLAC 无意义）</li>
 *   <li>应用编解码器固有限制（MP3≤48kHz≤2ch, Opus=48kHz, AAC≤96kHz）</li>
 *   <li>构建转码命令</li>
 * </ol>
 */
@Slf4j
@Service
public class TranscodeDeciderImpl implements TranscodeDecider {

    /** 内置转码命令：格式名 → {命令名, 默认比特率} */
    private static final Map<String, CommandDef> COMMANDS = Map.of(
            "mp3",  new CommandDef("mp3 audio", "mp3", 192),
            "opus", new CommandDef("opus audio", "opus", 128),
            "aac",  new CommandDef("aac audio", "aac", 256),
            "flac", new CommandDef("flac audio", "flac", 0)
    );

    private final PlaybackProperties properties;

    public TranscodeDeciderImpl(PlaybackProperties properties) {
        this.properties = properties;
    }

    @Override
    public TranscodeDecision decide(Song song, String reqFormat, int maxBitRate) {
        // 1. 无格式请求 → 直接播放
        String targetFmt = normalizeFormat(reqFormat);
        if (targetFmt == null || "raw".equalsIgnoreCase(reqFormat)) {
            return directPlay(song);
        }

        String srcFormat = normalizeFormat(song.getFileFormat());
        int srcBitrate = song.getBitrate() != null ? song.getBitrate() : 0;

        // 2. 查找目标格式对应的转码命令
        CommandDef cmd = COMMANDS.get(targetFmt);
        if (cmd == null) {
            log.debug("不支持的目标格式: {}，降级直接播放", targetFmt);
            return directPlay(song);
        }

        // 3. 源格式 = 目标格式，检查比特率
        if (targetFmt.equals(srcFormat) || targetFmt.equals(canonicalFormat(srcFormat))) {
            if (maxBitRate <= 0 || srcBitrate <= 0 || srcBitrate <= maxBitRate) {
                return directPlay(song);
            }
            // 源比特率超标，仍需转码降低比特率
        }

        // 4. 拒绝有损→无损
        if (FfmpegUtil.isLossy(srcFormat) && !FfmpegUtil.isLossy(cmd.format)) {
            log.debug("拒绝有损→无损转码: {}→{}，降级直接播放", srcFormat, cmd.format);
            return directPlay(song);
        }

        // 5. 确定目标参数
        int targetBitrate = maxBitRate > 0 ? Math.min(maxBitRate, cmd.defaultBitrate)
                : cmd.defaultBitrate;
        if (targetBitrate == 0 && maxBitRate > 0) {
            // 无损但客户端限制了比特率 → 用默认有损
            targetBitrate = 192;
        }

        // 6. 应用编解码器固有限制
        int srcSampleRate = song.getSampleRate() != null ? song.getSampleRate() : 0;
        int srcChannels = song.getChannels() != null ? song.getChannels() : 0;

        int targetSampleRate = clampSampleRate(cmd.format, srcSampleRate);
        int targetChannels = clampChannels(cmd.format, srcChannels);

        String mimeType = FfmpegUtil.mimeTypeForFormat(cmd.format);

        log.debug("转码决策: {} → {} @{}kbps {}Hz {}ch",
                srcFormat, cmd.format, targetBitrate, targetSampleRate, targetChannels);

        return TranscodeDecision.transcode(cmd.format, targetBitrate,
                targetSampleRate, targetChannels, mimeType, cmd.commandName);
    }

    // ── private helpers ──

    private TranscodeDecision directPlay(Song song) {
        String srcFmt = normalizeFormat(song.getFileFormat());
        String mime = ContentTypeResolver.resolve(song.getFileName());
        if (srcFmt != null) {
            mime = FfmpegUtil.mimeTypeForFormat(srcFmt);
        }
        return TranscodeDecision.directPlay(
                srcFmt != null ? srcFmt : "raw", mime);
    }

    /** 规范化格式名：移除 " audio" 后缀、统一小写 */
    private static String normalizeFormat(String format) {
        if (format == null || format.isBlank()) return null;
        String f = format.toLowerCase(Locale.ROOT).trim();
        if (f.endsWith(" audio")) f = f.substring(0, f.length() - 6);
        return f;
    }

    /** 将文件名扩展名规范化为格式名 */
    private static String canonicalFormat(String ext) {
        if (ext == null) return null;
        return switch (ext.toLowerCase(Locale.ROOT)) {
            case "m4a", "mp4" -> "aac";
            case "aif", "aiff" -> "aiff";
            default -> ext;
        };
    }

    /**
     * 应用编解码器采样率限制。
     *
     * <ul>
     *   <li>MP3: ≤ 48000 Hz</li>
     *   <li>Opus: 固定 48000 Hz</li>
     *   <li>AAC: ≤ 96000 Hz（但 >48k 通常无意义）</li>
     * </ul>
     */
    private static int clampSampleRate(String format, int srcRate) {
        if (srcRate <= 0) return 0;
        return switch (format) {
            case "mp3" -> Math.min(srcRate, 48000);
            case "opus" -> 48000;  // Opus 始终输出 48kHz
            case "aac" -> Math.min(srcRate, 96000);
            default -> 0;  // FLAC / WAV 不限制
        };
    }

    /**
     * 应用编解码器声道限制。
     *
     * <ul>
     *   <li>MP3: ≤ 2 ch</li>
     * </ul>
     */
    private static int clampChannels(String format, int srcCh) {
        if (srcCh <= 0) return 0;
        return switch (format) {
            case "mp3" -> Math.min(srcCh, 2);
            default -> 0;
        };
    }

    // ── inner types ──

    private record CommandDef(String commandName, String format, int defaultBitrate) {}
}
