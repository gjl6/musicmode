package com.gjl.music.playback.infra.transcoding;

import com.gjl.music.config.ConfigService;
import com.gjl.music.infra.util.FfmpegUtil;
import com.gjl.music.playback.model.TranscodeDecision;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;


@Component
public class TranscodeCommandBuilder {

    private final String ffmpegPath;

    public TranscodeCommandBuilder(ConfigService configService) {
        this.ffmpegPath = configService.getString("music.ffmpeg.path", "ffmpeg");
    }


    public List<String> build(Path sourceFile, TranscodeDecision decision, int offsetSec) {
        List<String> cmd = FfmpegUtil.commandPrefix(ffmpegPath);

                if (offsetSec > 0) {
            cmd.addAll(List.of("-ss", String.valueOf(offsetSec)));
        }

                cmd.addAll(List.of("-i", sourceFile.toString()));
        cmd.addAll(List.of("-map", "0:a:0"));

                cmd.addAll(List.of("-c:a", FfmpegUtil.codecForFormat(decision.targetFormat())));

                int br = decision.targetBitrate();
        if (br > 0) {
            cmd.addAll(List.of("-b:a", br + "k"));
        }

                if (decision.targetSampleRate() > 0) {
            cmd.addAll(List.of("-ar", String.valueOf(decision.targetSampleRate())));
        }

                if (decision.targetChannels() > 0) {
            cmd.addAll(List.of("-ac", String.valueOf(decision.targetChannels())));
        }

                cmd.addAll(List.of("-f", FfmpegUtil.containerForFormat(decision.targetFormat())));
        cmd.add("pipe:1");

        return cmd;
    }
}
