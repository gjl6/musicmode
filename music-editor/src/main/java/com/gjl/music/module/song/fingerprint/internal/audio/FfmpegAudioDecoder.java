package com.gjl.music.module.song.fingerprint.internal.audio;

import com.gjl.music.config.ConfigService;
import com.gjl.music.infra.util.AudioFileUtils;
import com.gjl.music.infra.util.FfmpegUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.DataInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于外部 FFmpeg 进程的音频解码器。
 * 通过 ffmpeg pipe:1 输出 f32le 11025Hz 单声道 PCM，
 * 与 fpcalc 使用相同的解码管线。
 */
@Slf4j
@Component
public class FfmpegAudioDecoder implements AudioDecoder {

    private static final int TARGET_SAMPLE_RATE = 11025;
    private final String ffmpegPath;

    public FfmpegAudioDecoder(ConfigService configService) {
        this.ffmpegPath = configService.getString("music.ffmpeg.path", "ffmpeg");
    }

    @Override
    public boolean supports(Path audioFile) {
        return AudioFileUtils.isAudioFile(audioFile);
    }

    @Override
    public DecodedAudio decode(Path audioFile) throws AudioDecodeException {
        List<String> cmd = FfmpegUtil.commandPrefix(ffmpegPath);
        cmd.addAll(List.of(
                "-i", audioFile.toString(),
                "-f", "f32le",           // 32-bit float little-endian PCM
                "-ar", String.valueOf(TARGET_SAMPLE_RATE),
                "-ac", "1",              // 单声道
                "-vn",
                "pipe:1"
        ));

        List<float[]> chunks = new ArrayList<>();
        int totalSamples = 0;

        try (InputStream in = FfmpegUtil.executeAndPipe(cmd);
             DataInputStream dis = new DataInputStream(in)) {

            byte[] buf = new byte[4096 * 4];  // 4096 float samples per chunk
            float[] chunk = new float[4096];
            int chunkIdx = 0;

            int bytesRead;
            while ((bytesRead = dis.read(buf)) > 0) {
                ByteBuffer bb = ByteBuffer.wrap(buf, 0, bytesRead)
                        .order(ByteOrder.LITTLE_ENDIAN);
                while (bb.hasRemaining()) {
                    chunk[chunkIdx++] = bb.getFloat();
                    if (chunkIdx == chunk.length) {
                        float[] copy = new float[chunk.length];
                        System.arraycopy(chunk, 0, copy, 0, chunk.length);
                        chunks.add(copy);
                        totalSamples += chunk.length;
                        chunkIdx = 0;
                    }
                }
            }

            // 最后不完整的 chunk
            if (chunkIdx > 0) {
                float[] copy = new float[chunkIdx];
                System.arraycopy(chunk, 0, copy, 0, chunkIdx);
                chunks.add(copy);
                totalSamples += chunkIdx;
            }

        } catch (Exception e) {
            throw new AudioDecodeException("FFmpeg 解码失败: " + audioFile, e);
        }

        // 合并 chunks
        float[] samples = new float[totalSamples];
        int offset = 0;
        for (float[] c : chunks) {
            System.arraycopy(c, 0, samples, offset, c.length);
            offset += c.length;
        }

        int duration = totalSamples / TARGET_SAMPLE_RATE;
        log.debug("FFmpeg 解码完成: {} -> {}Hz 单声道, {} 样本 ({}s)",
                audioFile.getFileName(), TARGET_SAMPLE_RATE, totalSamples, duration);

        return new DecodedAudio(samples, TARGET_SAMPLE_RATE, 1, duration);
    }
}
