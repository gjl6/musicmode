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


@Slf4j
@Service
public class TranscodeDeciderImpl implements TranscodeDecider {


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
                String targetFmt = normalizeFormat(reqFormat);
        if (targetFmt == null || "raw".equalsIgnoreCase(reqFormat)) {
            return directPlay(song);
        }

        String srcFormat = normalizeFormat(song.getFileFormat());
        int srcBitrate = song.getBitrate() != null ? song.getBitrate() : 0;

                CommandDef cmd = COMMANDS.get(targetFmt);
        if (cmd == null) {
            log.debug("不支持的目标格式: {}，降级直接播放", targetFmt);
            return directPlay(song);
        }

                if (targetFmt.equals(srcFormat) || targetFmt.equals(canonicalFormat(srcFormat))) {
            if (maxBitRate <= 0 || srcBitrate <= 0 || srcBitrate <= maxBitRate) {
                return directPlay(song);
            }
                    }

                if (FfmpegUtil.isLossy(srcFormat) && !FfmpegUtil.isLossy(cmd.format)) {
            log.debug("拒绝有损→无损转码: {}→{}，降级直接播放", srcFormat, cmd.format);
            return directPlay(song);
        }

                int targetBitrate = maxBitRate > 0 ? Math.min(maxBitRate, cmd.defaultBitrate)
                : cmd.defaultBitrate;
        if (targetBitrate == 0 && maxBitRate > 0) {
                        targetBitrate = 192;
        }

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


    private TranscodeDecision directPlay(Song song) {
        String srcFmt = normalizeFormat(song.getFileFormat());
        String mime = ContentTypeResolver.resolve(song.getFileName());
        if (srcFmt != null) {
            mime = FfmpegUtil.mimeTypeForFormat(srcFmt);
        }
        return TranscodeDecision.directPlay(
                srcFmt != null ? srcFmt : "raw", mime);
    }


    private static String normalizeFormat(String format) {
        if (format == null || format.isBlank()) return null;
        String f = format.toLowerCase(Locale.ROOT).trim();
        if (f.endsWith(" audio")) f = f.substring(0, f.length() - 6);
        return f;
    }


    private static String canonicalFormat(String ext) {
        if (ext == null) return null;
        return switch (ext.toLowerCase(Locale.ROOT)) {
            case "m4a", "mp4" -> "aac";
            case "aif", "aiff" -> "aiff";
            default -> ext;
        };
    }


    private static int clampSampleRate(String format, int srcRate) {
        if (srcRate <= 0) return 0;
        return switch (format) {
            case "mp3" -> Math.min(srcRate, 48000);
            case "opus" -> 48000;
            case "aac" -> Math.min(srcRate, 96000);
            default -> 0;
        };
    }


    private static int clampChannels(String format, int srcCh) {
        if (srcCh <= 0) return 0;
        return switch (format) {
            case "mp3" -> Math.min(srcCh, 2);
            default -> 0;
        };
    }


    private record CommandDef(String commandName, String format, int defaultBitrate) {}
}
