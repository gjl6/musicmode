package com.gjl.music.module.song.formatconvert;

import com.gjl.music.config.ConfigService;
import com.gjl.music.exception.ModuleException;
import com.gjl.music.infra.util.AudioFileUtils;
import com.gjl.music.infra.util.FileHashUtils;
import com.gjl.music.infra.util.FfmpegUtil;
import com.gjl.music.editor.mapper.SongManageMapper;
import com.gjl.music.model.*;
import com.gjl.music.module.FailurePolicy;
import com.gjl.music.module.song.support.MetadataGapFillingModule;
import com.gjl.music.module.song.support.SkipException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 格式转换模块实现 —— 通过外部 FFmpeg 进程将音频文件转换为目标格式。
 *
 * <p>继承 MetadataGapFillingModule，复用 DB 缓存 → 解析 → 处理 → 持久化 → 标签写回流程。
 * processItem 执行 FFmpeg pipe:1 转换并返回新文件的 MusicMetadata。</p>
 */
@Slf4j
@Component
public class FormatConvertModuleImpl extends MetadataGapFillingModule
        implements FormatConvertModule {

    private String targetFormat = DEFAULT_FORMAT;
    private int bitrate = DEFAULT_BITRATE;
    private int sampleRate = DEFAULT_SAMPLE_RATE;
    private int channels = DEFAULT_CHANNELS;
    private boolean deleteOriginal = DEFAULT_DELETE_ORIGINAL;
    private final String ffmpegPath;
    private final String ffprobePath;

    public FormatConvertModuleImpl(SongManageMapper songManageMapper, ConfigService configService) {
        super(songManageMapper);
        this.ffmpegPath = configService.getString("music.ffmpeg.path", "ffmpeg");
        this.ffprobePath = configService.getString("music.ffprobe.path", "ffprobe");
    }

    @Override
    public String name() {
        return "format-convert";
    }

    @Override
    public FailurePolicy failurePolicy() {
        return FailurePolicy.SKIP;
    }

    @Override public String label() { return "格式转换"; }

    @Override
    public List<Map<String, Object>> configSchema() {
        return List.of(
            Map.of("key", "targetFormat", "label", "目标格式", "type", "select",
                "default", "mp3",
                "options", List.of(
                    Map.of("value", "mp3", "label", "MP3"),
                    Map.of("value", "flac", "label", "FLAC"),
                    Map.of("value", "wav", "label", "WAV"),
                    Map.of("value", "aac", "label", "AAC"),
                    Map.of("value", "ogg", "label", "OGG"),
                    Map.of("value", "opus", "label", "Opus"),
                    Map.of("value", "alac", "label", "ALAC"),
                    Map.of("value", "aiff", "label", "AIFF"),
                    Map.of("value", "wma", "label", "WMA")
                )),
            Map.of("key", "bitrate", "label", "比特率(kbps)", "type", "number",
                "default", 320, "placeholder", "0=保持原始"),
            Map.of("key", "sampleRate", "label", "采样率(Hz)", "type", "number",
                "default", 0, "placeholder", "0=保持原始"),
            Map.of("key", "channels", "label", "声道数", "type", "number",
                "default", 0, "placeholder", "0=保持原始"),
            Map.of("key", "deleteOriginal", "label", "转换后删除源文件", "type", "boolean",
                "default", false)
        );
    }

    @Override
    public void configure(Map<String, Object> options) {
        if (options == null) return;

        if (options.containsKey(KEY_TARGET_FORMAT)) {
            String fmt = String.valueOf(options.get(KEY_TARGET_FORMAT)).toLowerCase();
            if (SUPPORTED_FORMATS.contains(fmt)) {
                this.targetFormat = fmt;
            } else {
                log.warn("不支持的目标格式: {}, 使用默认格式 {}", fmt, DEFAULT_FORMAT);
                this.targetFormat = DEFAULT_FORMAT;
            }
        }

        if (options.containsKey(KEY_BITRATE)) {
            this.bitrate = toInt(options.get(KEY_BITRATE), DEFAULT_BITRATE);
        }

        if (options.containsKey(KEY_SAMPLE_RATE)) {
            this.sampleRate = toInt(options.get(KEY_SAMPLE_RATE), DEFAULT_SAMPLE_RATE);
        }

        if (options.containsKey(KEY_CHANNELS)) {
            this.channels = toInt(options.get(KEY_CHANNELS), DEFAULT_CHANNELS);
        }

        if (options.containsKey(KEY_DELETE_ORIGINAL)) {
            this.deleteOriginal = toBool(options.get(KEY_DELETE_ORIGINAL), DEFAULT_DELETE_ORIGINAL);
        }

        log.info("format-convert 配置: targetFormat={}, bitrate={}, sampleRate={}, channels={}, deleteOriginal={}",
                targetFormat, bitrate, sampleRate, channels, deleteOriginal);
    }

    // ── GapFillingModule 模板方法 ──

    @Override
    protected MusicMetadata processItem(MusicMetadata meta) {
        if (meta == null || meta.getImmutableSongs().isEmpty()) {
            throw new ModuleException(name(), "输入元数据为空");
        }

        Song oldSong = meta.getImmutableSongs().get(0);
        Path sourcePath = toPath(oldSong.getFilePath());
        if (sourcePath == null || !Files.isRegularFile(sourcePath)) {
            throw new ModuleException(name(), "源文件不存在: " + oldSong.getFilePath());
        }

        // 同格式跳过
        String srcExt = AudioFileUtils.extension(sourcePath);
        String tgtExt = extensionForFormat(targetFormat);
        if (srcExt.equalsIgnoreCase(tgtExt)) {
            log.info("源文件已是目标格式 ({}), 跳过: {}", targetFormat, sourcePath.getFileName());
            throw new SkipException("已是目标格式: " + targetFormat);
        }

        // 确定输出路径
        Path targetPath = resolveOutputPath(sourcePath, tgtExt);
        log.info("转换: {} → {} ({} {})", sourcePath.getFileName(), targetFormat,
                bitrate > 0 && FfmpegUtil.isLossy(targetFormat) ? bitrate + "kbps" : "无损",
                targetPath.getFileName());

        // FFmpeg 转换
        convertWithFFmpeg(sourcePath, targetPath);

        // 计算新文件属性
        long newFileSize;
        try {
            newFileSize = Files.size(targetPath);
        } catch (IOException e) {
            throw new ModuleException(name(), "无法读取目标文件大小: " + targetPath, e);
        }
        String newFileHash = FileHashUtils.getFileHash(targetPath.toFile());

        // 构建新文件的 MusicMetadata —— 从源元数据复制所有标签，更新文件属性
        MusicMetadata newMeta = buildNewMetadata(meta, oldSong, targetPath, tgtExt, newFileSize, newFileHash);

        // 删除原始文件
        if (deleteOriginal) {
            try {
                Files.deleteIfExists(sourcePath);
                log.info("已删除原始文件: {}", sourcePath.getFileName());
            } catch (IOException e) {
                log.warn("删除原始文件失败: {}", sourcePath.getFileName(), e);
            }
        }

        return newMeta;
    }

    // ── FFmpeg 转换核心 ──

    private void convertWithFFmpeg(Path source, Path target) {
        FfmpegUtil.AudioProbe probe = FfmpegUtil.probe(ffprobePath, source);

        int outSampleRate = sampleRate > 0 ? sampleRate
                : (probe != null && probe.sampleRate() > 0 ? probe.sampleRate() : 44100);
        int outChannels = channels > 0 ? channels
                : (probe != null && probe.channels() > 0 ? probe.channels() : 2);

        List<String> cmd = FfmpegUtil.commandPrefix(ffmpegPath);
        cmd.addAll(List.of(
                "-i", source.toString(),
                "-f", FfmpegUtil.containerForFormat(targetFormat),
                "-c:a", FfmpegUtil.codecForFormat(targetFormat),
                "-ar", String.valueOf(outSampleRate),
                "-ac", String.valueOf(outChannels)
        ));

        // 有损格式设置比特率
        if (FfmpegUtil.isLossy(targetFormat) && bitrate > 0) {
            cmd.addAll(List.of("-b:a", bitrate + "k"));
        }

        // FLAC 压缩级别
        if ("flac".equals(targetFormat)) {
            cmd.addAll(List.of("-compression_level", "5"));
        }

        cmd.addAll(List.of("-vn", "pipe:1"));

        try (InputStream in = FfmpegUtil.executeAndPipe(cmd);
             OutputStream out = Files.newOutputStream(target)) {
            in.transferTo(out);
            log.debug("FFmpeg 转换完成: {} -> {}", source.getFileName(), target.getFileName());
        } catch (IOException e) {
            throw new ModuleException(name(), "FFmpeg 转换失败: " + source.getFileName()
                    + " → " + targetFormat + " - " + e.getMessage(), e);
        }
    }

    // ── 元数据构建 ──

    /**
     * 从源元数据构建新文件元数据 —— 复制所有标签，更新文件级属性。
     */
    private MusicMetadata buildNewMetadata(MusicMetadata oldMeta, Song oldSong,
                                           Path targetPath, String tgtExt,
                                           long newFileSize, String newFileHash) {
        MusicMetadata newMeta = new MusicMetadata();

        // Song —— 从旧 Song 复制元数据字段，更新文件属性
        Song.SongBuilder sb = oldSong.toBuilder()
                .id(UUID.randomUUID().toString())   // 新文件新 ID
                .filePath(targetPath.toString())
                .fileName(targetPath.getFileName().toString())
                .fileFormat(tgtExt)
                .fileSize(newFileSize)
                .fileHash(newFileHash)
                .fingerprint(null);                 // 指纹需要重新计算

        if (FfmpegUtil.isLossy(targetFormat)) {
            sb.bitrate(bitrate);
        }
        if (sampleRate > 0) {
            sb.sampleRate(sampleRate);
        }
        if (channels > 0) {
            sb.channels(channels);
        }
        // 无损格式保留原始位深，有损格式不适用
        if (FfmpegUtil.isLossy(targetFormat)) {
            sb.bitsPerSample(null);
        }

        newMeta.addSong(sb.build());

        // 复制 Album、Artist、Style、Lyric（不可变副本）
        for (Album a : oldMeta.getImmutableAlbums()) {
            newMeta.addAlbum(a);
        }
        for (Artist a : oldMeta.getImmutableArtists()) {
            newMeta.addArtist(a);
        }
        for (Style s : oldMeta.getImmutableStyles()) {
            newMeta.addStyle(s);
        }
        for (Lyric l : oldMeta.getImmutableLyrics()) {
            newMeta.addLyric(l);
        }

        return newMeta;
    }

    // ── 路径工具 ──

    private Path resolveOutputPath(Path source, String tgtExt) {
        String baseName = stripExtension(source.getFileName().toString());
        Path parent = source.getParent();
        if (parent == null) {
            parent = Path.of(".");
        }

        Path candidate = parent.resolve(baseName + "." + tgtExt);

        // 冲突处理：目标文件已存在且不是源文件本身 → 加 _converted 后缀
        if (Files.exists(candidate) && !candidate.equals(source)) {
            String altName = baseName + "_converted." + tgtExt;
            candidate = parent.resolve(altName);
            log.info("目标文件已存在，使用替代名称: {}", candidate.getFileName());
        }

        return candidate;
    }

    // ── 格式映射工具（委托 FfmpegUtil）──

    static String extensionForFormat(String format) {
        return FfmpegUtil.extensionForFormat(format);
    }

    // 注意：formatNameForFFmpeg / codecForFormat 已迁移至 FfmpegUtil，
    // convertWithFFmpeg 直接调用 FfmpegUtil.containerForFormat / FfmpegUtil.codecForFormat

    // ── 辅助 ──

    private static Path toPath(String filePath) {
        if (filePath == null || filePath.isBlank()) return null;
        return Path.of(filePath);
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private static int toInt(Object value, int defaultValue) {
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    private static boolean toBool(Object value, boolean defaultValue) {
        if (value instanceof Boolean b) return b;
        if (value instanceof String s) return Boolean.parseBoolean(s);
        return defaultValue;
    }
}
