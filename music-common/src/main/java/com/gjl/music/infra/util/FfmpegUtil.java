package com.gjl.music.infra.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;


public final class FfmpegUtil {

    private FfmpegUtil() {}


    public static final Set<String> LOSSY_FORMATS = Set.of("mp3", "aac", "ogg", "opus", "wma");


    public static final Set<String> LOSSLESS_FORMATS = Set.of("flac", "wav", "alac", "aiff", "wv");


    public static final Set<String> SUPPORTED_FORMATS;
    static {
        Set<String> s = new LinkedHashSet<>(LOSSY_FORMATS);
        s.addAll(LOSSLESS_FORMATS);
        SUPPORTED_FORMATS = Collections.unmodifiableSet(s);
    }


    public static String containerForFormat(String format) {
        return switch (format.toLowerCase(Locale.ROOT)) {
            case "aac"  -> "adts";
            case "alac" -> "ipod";
            case "wma"  -> "asf";
            default     -> format;
        };
    }


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


    public static int defaultBitrateForFormat(String format) {
        return switch (format.toLowerCase(Locale.ROOT)) {
            case "opus" -> 128;
            case "mp3", "aac", "ogg", "wma" -> 192;
            default -> 0;
        };
    }


    public static String mimeTypeForFormat(String format) {
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


    public static String extensionForFormat(String format) {
        return switch (format.toLowerCase(Locale.ROOT)) {
            case "aac", "alac" -> "m4a";
            default -> format;
        };
    }


    public static boolean isLossy(String format) {
        return LOSSY_FORMATS.contains(format.toLowerCase(Locale.ROOT));
    }


    public static List<String> commandPrefix(String ffmpegPath) {
        return new ArrayList<>(List.of(ffmpegPath, "-y", "-v", "quiet", "-nostdin"));
    }


    public static List<String> probeCommandPrefix(String ffprobePath) {
        return new ArrayList<>(List.of(ffprobePath, "-v", "quiet", "-print_format", "json"));
    }


    public static final long DEFAULT_TIMEOUT_SECONDS = 300;


    public static InputStream executeAndPipe(List<String> command) {
        Process process;
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectError(ProcessBuilder.Redirect.PIPE);
            process = pb.start();
        } catch (IOException e) {
            throw new FfmpegException("无法启动 FFmpeg 进程: " + e.getMessage(), e);
        }

        return new FfmpegInputStream(process);
    }


    private static final ObjectMapper JSON = new ObjectMapper();


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
                    (int) fmt.path("bit_rate").asLong(0) / 1000,
                    fmt.path("duration").asDouble(0.0),
                    audioStream.path("bits_per_raw_sample").asInt(-1)
            );
        } catch (Exception e) {
            return null;
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }


    public record AudioProbe(
            String format,
            String codec,
            int sampleRate,
            int channels,
            int bitrate,
            double duration,
            int bitDepth
    ) {}


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
                                try (InputStream err = process.getErrorStream()) {
                    err.transferTo(OutputStream.nullOutputStream());
                } catch (Exception ignored) {
                }
            }
        }
    }


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
