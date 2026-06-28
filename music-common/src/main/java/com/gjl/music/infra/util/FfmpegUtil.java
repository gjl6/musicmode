package com.gjl.music.infra.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * FFmpeg / ffprobe 命令行工具 —— 纯静态方法，零 Spring 依赖。
 *
 * <h3>统一模式</h3>
 * 所有 FFmpeg 调用都通过 {@link #executeAndPipe(List)} 执行：
 * <pre>{@code
 *   List<String> cmd = FfmpegUtil.commandPrefix(ffmpegPath);
 *   cmd.addAll(List.of("-i", input, "-f", "mp3", "pipe:1"));
 *   try (InputStream in = FfmpegUtil.executeAndPipe(cmd)) {
 *       in.transferTo(outputStream);  // 文件 / HTTP 响应 / 内存
 *   }
 * }</pre>
 *
 * <p>调用方负责关闭返回的 InputStream —— 关闭时自动销毁子进程。</p>
 */
public final class FfmpegUtil {

    private FfmpegUtil() {}

    // ═══════════════════════════════════════════════════════════════
    // 格式映射
    // ═══════════════════════════════════════════════════════════════

    /** 有损格式集合 */
    public static final Set<String> LOSSY_FORMATS = Set.of("mp3", "aac", "ogg", "opus", "wma");

    /** 无损格式集合 */
    public static final Set<String> LOSSLESS_FORMATS = Set.of("flac", "wav", "alac", "aiff", "wv");

    /** 所有支持的输出格式 */
    public static final Set<String> SUPPORTED_FORMATS;
    static {
        Set<String> s = new LinkedHashSet<>(LOSSY_FORMATS);
        s.addAll(LOSSLESS_FORMATS);
        SUPPORTED_FORMATS = Collections.unmodifiableSet(s);
    }

    /**
     * 格式 → FFmpeg 容器名。
     * 如 "mp3"→"mp3", "aac"→"adts", "alac"→"ipod", "wma"→"asf"
     */
    public static String containerForFormat(String format) {
        return switch (format.toLowerCase(Locale.ROOT)) {
            case "aac"  -> "adts";
            case "alac" -> "ipod";
            case "wma"  -> "asf";
            default     -> format;  // mp3, flac, wav, ogg, opus, wv, aiff
        };
    }

    /**
     * 格式 → FFmpeg 音频编码器名（CLI 名称）。
     * 如 "mp3"→"libmp3lame", "flac"→"flac"
     */
    public static String codecForFormat(String format) {
        return switch (format.toLowerCase(Locale.ROOT)) {
            case "mp3"  -> "libmp3lame";
            case "flac" -> "flac";
            case "wav"  -> "pcm_s16le";
            case "aac"  -> "aac";
            case "ogg"  -> "libvorbis";
            case "opus" -> "libopus";
            case "alac" -> "alac";
            case "aiff" -> "pcm_s16be";
            case "wma"  -> "wmav2";
            case "wv"   -> "wavpack";
            default     -> "libmp3lame";
        };
    }

    /**
     * 格式 → 默认比特率（kbps）。无损格式返回 0。
     */
    public static int defaultBitrateForFormat(String format) {
        return switch (format.toLowerCase(Locale.ROOT)) {
            case "opus" -> 128;
            case "mp3", "aac", "ogg", "wma" -> 192;
            default -> 0;  // 无损
        };
    }

    /**
     * 格式 → MIME type。
     * 如 "mp3"→"audio/mpeg", "flac"→"audio/flac"
     */
    public static String mimeTypeForFormat(String format) {
        if (format == null || format.isBlank()) {
            return "application/octet-stream";
        }
        return switch (format.toLowerCase(Locale.ROOT)) {
            case "mp3"  -> "audio/mpeg";
            case "flac" -> "audio/flac";
            case "wav"  -> "audio/wav";
            case "aac", "alac", "m4a" -> "audio/mp4";
            case "ogg", "opus" -> "audio/ogg";
            case "wma"  -> "audio/x-ms-wma";
            default     -> "application/octet-stream";
        };
    }

    /**
     * 格式 → 文件扩展名（可能与格式名不同）。
     * 如 "aac"→"m4a", "alac"→"m4a"
     */
    public static String extensionForFormat(String format) {
        return switch (format.toLowerCase(Locale.ROOT)) {
            case "aac", "alac" -> "m4a";
            default -> format;
        };
    }

    /** 格式是否为有损压缩 */
    public static boolean isLossy(String format) {
        return LOSSY_FORMATS.contains(format.toLowerCase(Locale.ROOT));
    }

    // ═══════════════════════════════════════════════════════════════
    // 命令构建
    // ═══════════════════════════════════════════════════════════════

    /**
     * 构建 ffmpeg 命令前缀。
     * @param ffmpegPath FFmpeg 可执行文件路径（如 "ffmpeg" 或 "/usr/bin/ffmpeg"）
     * @return ["ffmpeg", "-y", "-v", "quiet", "-nostdin"]
     */
    public static List<String> commandPrefix(String ffmpegPath) {
        return new ArrayList<>(List.of(ffmpegPath, "-y", "-v", "quiet", "-nostdin"));
    }

    /**
     * 构建 ffprobe 命令前缀。
     * @param ffprobePath ffprobe 可执行文件路径
     */
    public static List<String> probeCommandPrefix(String ffprobePath) {
        return new ArrayList<>(List.of(ffprobePath, "-v", "quiet", "-print_format", "json"));
    }

    // ═══════════════════════════════════════════════════════════════
    // 进程执行
    // ═══════════════════════════════════════════════════════════════

    /** 默认子进程超时（5 分钟），适用于单文件批量转换 */
    public static final long DEFAULT_TIMEOUT_SECONDS = 300;

    /**
     * 启动 FFmpeg 子进程，stdout → pipe:1，返回 InputStream。
     *
     * <p>调用方 <b>必须</b> 用 try-with-resources 包裹返回的 InputStream：
     * 关闭 InputStream 时自动 {@link Process#destroyForcibly()} 子进程。</p>
     *
     * @param command 完整命令行（已包含 ffmpeg 路径和所有参数）
     * @return 连接到子进程 stdout 的 InputStream（带自动销毁包装）
     * @throws FfmpegException 进程启动失败
     */
    public static InputStream executeAndPipe(List<String> command) {
        Process process;
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectError(ProcessBuilder.Redirect.PIPE);  // stderr 单独读取用于错误报告
            process = pb.start();
        } catch (IOException e) {
            throw new FfmpegException("无法启动 FFmpeg 进程: " + e.getMessage(), e);
        }

        return new FfmpegInputStream(process);
    }

    // ═══════════════════════════════════════════════════════════════
    // ffprobe 探测
    // ═══════════════════════════════════════════════════════════════

    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * 用 ffprobe 探测音频文件属性。
     *
     * <pre>
     *   ffprobe -v quiet -print_format json -show_format -show_streams file
     * </pre>
     *
     * @param ffprobePath ffprobe 可执行文件路径
     * @param audioFile   音频文件路径
     * @return 音频属性，探测失败返回 {@code null}
     */
    public static AudioProbe probe(String ffprobePath, Path audioFile) {
        if (!Files.isRegularFile(audioFile)) return null;

        List<String> cmd = probeCommandPrefix(ffprobePath);
        cmd.addAll(List.of("-show_format", "-show_streams", audioFile.toString()));

        Process process;
        try {
            process = new ProcessBuilder(cmd)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
        } catch (IOException e) {
            return null;
        }

        try (InputStream in = process.getInputStream()) {
            JsonNode root = JSON.readTree(in);

            JsonNode fmt = root.path("format");
            JsonNode streams = root.path("streams");
            JsonNode audioStream = null;
            if (streams.isArray()) {
                for (JsonNode s : streams) {
                    if ("audio".equals(s.path("codec_type").asText())) {
                        audioStream = s;
                        break;
                    }
                }
            }
            if (audioStream == null) audioStream = streams.path(0);

            return new AudioProbe(
                    fmt.path("format_name").asText(null),
                    audioStream.path("codec_name").asText(null),
                    audioStream.path("sample_rate").asInt(0),
                    audioStream.path("channels").asInt(0),
                    (int) fmt.path("bit_rate").asLong(0) / 1000,  // bps → kbps
                    fmt.path("duration").asDouble(0.0),
                    audioStream.path("bits_per_raw_sample").asInt(-1)
            );
        } catch (Exception e) {
            return null;
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    /**
     * ffprobe 探测结果。
     *
     * @param format     容器格式名（如 "mp3", "flac"）
     * @param codec      音频编码器名（如 "mp3", "flac"）
     * @param sampleRate 采样率（Hz），未知时为 0
     * @param channels   声道数，未知时为 0
     * @param bitrate    比特率（kbps），未知时为 0
     * @param duration   时长（秒），未知时为 0.0
     * @param bitDepth   位深度（如 16/24/32），未知时为 -1
     */
    public record AudioProbe(
            String format,
            String codec,
            int sampleRate,
            int channels,
            int bitrate,
            double duration,
            int bitDepth
    ) {}

    // ═══════════════════════════════════════════════════════════════
    // 内部类
    // ═══════════════════════════════════════════════════════════════

    /**
     * 包装子进程 stdout InputStream，关闭时自动销毁子进程。
     */
    private static final class FfmpegInputStream extends InputStream {

        private final Process process;
        private final InputStream delegate;

        FfmpegInputStream(Process process) {
            this.process = process;
            this.delegate = process.getInputStream();
        }

        @Override
        public int read() throws IOException {
            return delegate.read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return delegate.read(b, off, len);
        }

        @Override
        public long transferTo(OutputStream out) throws IOException {
            return delegate.transferTo(out);
        }

        @Override
        public void close() throws IOException {
            try {
                delegate.close();
            } finally {
                if (process.isAlive()) {
                    process.destroyForcibly();
                }
                // 读取 stderr 用于调试（不阻塞，best-effort）
                try (InputStream err = process.getErrorStream()) {
                    err.transferTo(OutputStream.nullOutputStream());
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * FFmpeg 执行异常。
     */
    public static class FfmpegException extends RuntimeException {
        private final int exitCode;

        public FfmpegException(String message) {
            super(message);
            this.exitCode = -1;
        }

        public FfmpegException(String message, Throwable cause) {
            super(message, cause);
            this.exitCode = -1;
        }

        public FfmpegException(int exitCode, String stderr) {
            super("FFmpeg 退出码 " + exitCode + (stderr != null && !stderr.isBlank()
                    ? ": " + stderr.strip().lines().findFirst().orElse("")
                    : ""));
            this.exitCode = exitCode;
        }

        public int getExitCode() {
            return exitCode;
        }
    }
}
