package com.gjl.music.module.song.cuesplit;

import com.gjl.music.config.ConfigService;
import com.gjl.music.exception.ModuleException;
import com.gjl.music.infra.util.AudioFileUtils;
import com.gjl.music.infra.util.FfmpegUtil;
import com.gjl.music.model.*;
import com.gjl.music.module.FailurePolicy;
import com.gjl.music.module.Module;
import com.gjl.music.infra.pipeline.NodeContext;
import com.gjl.music.infra.pipeline.NodeHandler;
import com.gjl.music.infra.pipeline.NodeResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * 音轨分割实现 —— 解析 CUE 文件并用 FFmpeg 将整轨音频分割为独立音轨。
 *
 * <p>流程：
 * <ol>
 *   <li>扫描目标目录，查找 .cue 文件</li>
 *   <li>解析 CUE，获取每轨标题、艺术家、起止时间</li>
 *   <li>对每轨，用 FFmpegFrameGrabber 定位 + FFmpegFrameRecorder 提取</li>
 *   <li>为每轨构建 MusicMetadata，通过 writer 模块写入 ID3 标签</li>
 * </ol>
 */
@Slf4j
@Component
public class CueSplitModuleImpl implements CueSplitModule, NodeHandler {

    private String outputFormat = DEFAULT_OUTPUT_FORMAT;
    private boolean deleteSource = DEFAULT_DELETE_SOURCE;
    private boolean writeTags = DEFAULT_WRITE_TAGS;
    private final String ffmpegPath;
    private final String ffprobePath;

    public CueSplitModuleImpl(ConfigService configService) {
        this.ffmpegPath = configService.getString("music.ffmpeg.path", "ffmpeg");
        this.ffprobePath = configService.getString("music.ffprobe.path", "ffprobe");
    }

    // ── Module 元数据 ──

    @Override
    public String name() { return "cue-split"; }

    @Override
    public FailurePolicy failurePolicy() { return FailurePolicy.SKIP; }

    @Override public String label() { return "CUE 分轨"; }

    @Override
    public List<Map<String, Object>> configSchema() {
        return List.of(
            Map.of("key", "outputFormat", "label", "输出格式", "type", "select",
                "default", "keep",
                "options", List.of(
                    Map.of("value", "keep", "label", "保持原始格式"),
                    Map.of("value", "mp3", "label", "MP3"),
                    Map.of("value", "flac", "label", "FLAC"),
                    Map.of("value", "wav", "label", "WAV")
                )),
            Map.of("key", "deleteSource", "label", "分割后删除源文件", "type", "boolean",
                "default", false),
            Map.of("key", "writeTags", "label", "写入标签到分割文件", "type", "boolean",
                "default", true)
        );
    }

    @Override
    public Module.ExecutorType executorType() { return Module.ExecutorType.VIRTUAL; }

    @Override
    public void configure(Map<String, Object> options) {
        if (options == null) return;
        this.outputFormat = str(options, KEY_OUTPUT_FORMAT, DEFAULT_OUTPUT_FORMAT).toLowerCase();
        this.deleteSource = bool(options, KEY_DELETE_SOURCE, DEFAULT_DELETE_SOURCE);
        this.writeTags  = bool(options, KEY_WRITE_TAGS, DEFAULT_WRITE_TAGS);
        log.info("cue-split 配置: outputFormat={}, deleteSource={}, writeTags={}",
                outputFormat, deleteSource, writeTags);
    }

    // ── NodeHandler 入口 ──

    @Override
    public NodeResult execute(NodeContext ctx) throws Exception {
        // 从上下文加载模块级配置
        Map<String, Object> allOptions = ctx.getSlot("options");
        if (allOptions != null) {
            Object cfg = allOptions.get(name());
            if (cfg instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) map;
                configure(m);
            }
        }

        NodeResult result = new NodeResult();
        List<Path> paths = resolvePaths(ctx);
        if (paths.isEmpty()) {
            log.warn("cue-split: 没有输入目录");
            return result;
        }

        // 收集所有目录，去重
        Set<Path> dirs = new LinkedHashSet<>();
        for (Path p : paths) {
            if (Files.isDirectory(p)) {
                dirs.add(p);
            } else if (Files.isRegularFile(p)) {
                Path parent = p.getParent();
                if (parent != null) dirs.add(parent);
            }
        }

        int totalTracks = 0;
        for (Path dir : dirs) {
            List<Path> cueFiles = findCueFiles(dir);
            if (cueFiles.isEmpty()) {
                log.info("cue-split: 目录 {} 中未找到 .cue 文件", dir.getFileName());
                continue;
            }
            for (Path cuePath : cueFiles) {
                int n = processCueFile(ctx, cuePath, result);
                totalTracks += n;
            }
        }

        log.info("cue-split: 完成, 共分割 {} 轨", totalTracks);
        return result;
    }

    // ── 核心处理 ──

    private int processCueFile(NodeContext ctx, Path cuePath, NodeResult result) {
        // 1. 解析 CUE
        CueSheet sheet;
        try {
            sheet = CueParser.parse(cuePath);
        } catch (CueParser.CueParseException e) {
            log.error("CUE 解析失败: {} - {}", cuePath.getFileName(), e.getMessage());
            recordResult(ctx, result, cuePath.toString(), false, "CUE 解析失败: " + e.getMessage());
            return 0;
        } catch (IOException e) {
            log.error("读取 CUE 文件失败: {}", cuePath, e);
            recordResult(ctx, result, cuePath.toString(), false, "读取失败: " + e.getMessage());
            return 0;
        }

        Path cueDir = cuePath.getParent();
        if (cueDir == null) cueDir = Path.of(".");

        log.info("cue-split: 解析 CUE [{}], {} 个 FILE, 艺术家={}, 专辑={}",
                cuePath.getFileName(), sheet.getFiles().size(),
                sheet.getPerformer(), sheet.getTitle());

        int trackCount = 0;

        // 2. 遍历所有 FILE 条目
        for (CueSheet.CueFile cueFile : sheet.getFiles()) {
            Path audioFile = resolveAudioFile(cueDir, cueFile.getFileName());
            if (!Files.isRegularFile(audioFile)) {
                log.error("cue-split: 音频文件不存在: {}", audioFile);
                recordResult(ctx, result, cuePath.toString(), false, "音频文件不存在: " + cueFile.getFileName());
                continue;
            }

            String sourceFormat = AudioFileUtils.extension(audioFile).toLowerCase();
            String actualOutputFormat = outputFormat.equals("keep") ? sourceFormat : outputFormat;

            // 3. 提取音频属性（源格式、码率、采样率等）
            SourceAudioInfo srcInfo = probeSource(audioFile, sourceFormat);
            if (srcInfo == null) {
                recordResult(ctx, result, cuePath.toString(), false, "无法读取源文件属性: " + audioFile);
                continue;
            }

            List<CueSheet.CueTrack> tracks = cueFile.getTracks();
            if (tracks.isEmpty()) {
                log.warn("cue-split: FILE {} 中没有 TRACK 条目", cueFile.getFileName());
                continue;
            }

            // 4. 逐轨提取
            Map<String, MusicMetadata> toWrite = new LinkedHashMap<>();
            Map<String, MusicMetadata> toPersist = new LinkedHashMap<>();

            for (int i = 0; i < tracks.size(); i++) {
                if (ctx.isCancelled()) break;
                try { ctx.checkPause(); } catch (NodeContext.CancelledException ce) { break; }

                CueSheet.CueTrack track = tracks.get(i);
                int startMs = track.getIndex01Ms();

                // 结束时间 = 下一轨的 INDEX 01（最后一轨用文件总时长）
                int endMs;
                if (i + 1 < tracks.size()) {
                    int nextStart = tracks.get(i + 1).getIndex01Ms();
                    endMs = nextStart > startMs ? nextStart : startMs + 60000;
                } else {
                    endMs = srcInfo.durationMs > startMs ? srcInfo.durationMs : startMs + 60000;
                }

                int durationMs = endMs - startMs;

                // 输出文件名
                String trackTitle = track.getTitle() != null ? track.getTitle()
                        : String.format("Track %02d", track.getTrackNumber());
                String safeName = sanitizeFileName(String.format("%02d. %s",
                        track.getTrackNumber(), trackTitle));
                String outExt = extForFormat(actualOutputFormat);
                Path outputPath = cueDir.resolve(safeName + "." + outExt);

                // 冲突处理
                if (Files.exists(outputPath)) {
                    outputPath = cueDir.resolve(safeName + "_split." + outExt);
                }

                log.info("cue-split: 提取 TRACK {:02d} [{:02d}:{:02d} → {:02d}:{:02d}] → {}",
                        track.getTrackNumber(),
                        startMs / 60000, (startMs % 60000) / 1000,
                        endMs / 60000, (endMs % 60000) / 1000,
                        outputPath.getFileName());

                try {
                    extractSegment(audioFile, outputPath, startMs, endMs,
                            sourceFormat, actualOutputFormat, srcInfo);
                } catch (Exception e) {
                    log.error("cue-split: 提取失败 TRACK {:02d}: {}", track.getTrackNumber(), e.getMessage());
                    recordResult(ctx, result, outputPath.toString(), false, e.getMessage());
                    continue;
                }

                // 5. 构建 MusicMetadata
                MusicMetadata meta = buildMetadata(track, sheet, outputPath,
                        outExt, actualOutputFormat, durationMs, srcInfo);
                toWrite.put(outputPath.toString(), meta);
                toPersist.put(outputPath.toString(), meta);
                recordResult(ctx, result, outputPath.toString(), true, null);
                trackCount++;
            }

            // 6. 写回 DB
            if (!toPersist.isEmpty()) {
                try {
                    ctx.invoke("db-operator", toPersist);
                } catch (Exception e) {
                    log.warn("cue-split: DB 写入失败: {}", e.getMessage());
                }
            }

            // 7. 写入标签
            if (writeTags && !toWrite.isEmpty()) {
                try {
                    ctx.invoke("writer", toWrite);
                } catch (Exception e) {
                    log.warn("cue-split: 标签写入失败: {}", e.getMessage());
                }
            }

            // 8. 删除源文件（可选）
            if (deleteSource) {
                try {
                    Files.deleteIfExists(audioFile);
                    log.info("cue-split: 已删除源文件: {}", audioFile.getFileName());
                } catch (IOException e) {
                    log.warn("cue-split: 删除源文件失败: {}", audioFile.getFileName(), e);
                }
            }
        }

        return trackCount;
    }

    // ── FFmpeg 段提取 ──

    /**
     * 从源文件提取指定时间范围的音频段，写入目标文件。
     * 使用 FFmpeg 命令行：-ss seek → -t 时长 → -c:a copy 流复制（无重编码，无损且极快）。
     */
    private void extractSegment(Path source, Path output, long startMs, long endMs,
                                 String sourceFormat, String targetFormat,
                                 SourceAudioInfo srcInfo) {
        double startSec = startMs / 1000.0;
        double durationSec = (endMs - startMs) / 1000.0;

        List<String> cmd = FfmpegUtil.commandPrefix(ffmpegPath);
        cmd.addAll(List.of(
                "-ss", String.format(Locale.ROOT, "%.3f", startSec),
                "-i", source.toString(),
                "-t", String.format(Locale.ROOT, "%.3f", durationSec),
                "-f", FfmpegUtil.containerForFormat(targetFormat)
        ));

        // 同格式时流复制（无损无重编码），否则重编码
        if (targetFormat.equals(sourceFormat)) {
            cmd.addAll(List.of("-c:a", "copy"));
        } else {
            cmd.addAll(List.of("-c:a", FfmpegUtil.codecForFormat(targetFormat)));
            if (FfmpegUtil.isLossy(targetFormat) && srcInfo.bitrate > 0) {
                cmd.addAll(List.of("-b:a", srcInfo.bitrate + "k"));
            }
        }

        cmd.addAll(List.of("-vn", "pipe:1"));

        try (InputStream in = FfmpegUtil.executeAndPipe(cmd);
             OutputStream out = Files.newOutputStream(output)) {
            in.transferTo(out);
            log.debug("cue-split: 段提取完成 → {}", output.getFileName());
        } catch (IOException e) {
            throw new ModuleException(name(), "FFmpeg 提取失败: " + source.getFileName()
                    + " → " + output.getFileName() + " - " + e.getMessage(), e);
        }
    }

    // ── 源文件探测 ──

    private SourceAudioInfo probeSource(Path audioFile, String format) {
        FfmpegUtil.AudioProbe probe = FfmpegUtil.probe(ffprobePath, audioFile);
        if (probe == null) return null;

        SourceAudioInfo info = new SourceAudioInfo();
        info.format = format;
        info.sampleRate = probe.sampleRate();
        info.channels = probe.channels();
        info.bitrate = probe.bitrate();       // kbps
        info.durationMs = (int) (probe.duration() * 1000);
        return info;
    }

    static class SourceAudioInfo {
        int sampleRate;
        int channels;
        int bitrate;    // kbps
        int durationMs; // 毫秒
        String format;
    }

    // ── 元数据构建 ──

    private MusicMetadata buildMetadata(CueSheet.CueTrack track, CueSheet sheet,
                                         Path outputPath, String ext, String fmt,
                                         int durationMs, SourceAudioInfo srcInfo) {
        MusicMetadata meta = new MusicMetadata();

        // Song
        Song.SongBuilder sb = Song.builder()
                .filePath(outputPath.toString())
                .fileName(outputPath.getFileName().toString())
                .fileFormat(ext)
                .title(track.getTitle())
                .trackNumber(track.getTrackNumber())
                .discNumber(1)
                .duration(durationMs / 1000)
                .bitrate(srcInfo.bitrate > 0 ? srcInfo.bitrate : null)
                .sampleRate(srcInfo.sampleRate > 0 ? srcInfo.sampleRate : null)
                .channels(srcInfo.channels > 0 ? srcInfo.channels : null);
        meta.addSong(sb.build());

        // Album
        if (sheet.getTitle() != null && !sheet.getTitle().isBlank()) {
            Album.AlbumBuilder ab = Album.builder().albumName(sheet.getTitle());
            meta.addAlbum(ab.build());
        }

        // Artist: 优先 TRACK 级 PERFORMER，回退 ALBUM 级 PERFORMER
        String artistName = track.getPerformer();
        if (artistName == null || artistName.isBlank()) {
            artistName = sheet.getPerformer();
        }
        if (artistName != null && !artistName.isBlank()) {
            meta.addArtist(Artist.builder().artistName(artistName).build());
        }

        return meta;
    }

    // ── 路径解析 ──

    @SuppressWarnings("unchecked")
    private List<Path> resolvePaths(NodeContext ctx) {
        // 从 scanner 输出获取
        Object scannerOut = ctx.getSlot("node.scanner.output");
        if (scannerOut instanceof List<?> list && !list.isEmpty()
                && list.getFirst() instanceof Path) {
            return (List<Path>) list;
        }
        // 从 input.paths 获取
        Path[] inputPaths = ctx.getSlot("input.paths");
        if (inputPaths != null && inputPaths.length > 0) {
            return List.of(inputPaths);
        }
        return List.of();
    }

    /**
     * 根据 FILE 名找到实际音频文件。
     * 优先 CUE 目录下同名文件，其次尝试常见扩展名。
     */
    private Path resolveAudioFile(Path cueDir, String fileName) {
        Path direct = cueDir.resolve(fileName);
        if (Files.isRegularFile(direct)) return direct;

        // 尝试常见扩展名
        for (String ext : List.of("flac", "wav", "mp3", "ape", "wv", "aiff", "aif", "m4a", "ogg")) {
            Path candidate = cueDir.resolve(fileName + "." + ext);
            if (Files.isRegularFile(candidate)) return candidate;
            // 去掉扩展名后再试
            String base = stripExt(fileName);
            if (!base.equals(fileName)) {
                candidate = cueDir.resolve(base + "." + ext);
                if (Files.isRegularFile(candidate)) return candidate;
            }
        }

        return direct; // 返回原始路径，后续会检查
    }

    /** 在目录中查找所有 .cue 文件 */
    private List<Path> findCueFiles(Path dir) throws IOException {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".cue"))
                    .sorted()
                    .toList();
        }
    }

    // ── 格式映射（委托 FfmpegUtil）──

    static String extForFormat(String format) {
        return FfmpegUtil.extensionForFormat(format);
    }

    // 注意：fmtNameForFFmpeg / codecForFormat / isLossy 已迁移至 FfmpegUtil

    // ── 辅助 ──

    private static String sanitizeFileName(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_").strip();
    }

    private static String stripExt(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private static String str(Map<String, Object> m, String k, String def) {
        Object v = m.get(k);
        return v instanceof String s && !s.isBlank() ? s : def;
    }

    private static boolean bool(Map<String, Object> m, String k, boolean def) {
        Object v = m.get(k);
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) return Boolean.parseBoolean(s);
        return def;
    }

    /** 同时记录到 NodeResult 并通过 NodeContext 实时上报进度 */
    private static void recordResult(NodeContext ctx, NodeResult result,
                                      String itemKey, boolean success, String errorMessage) {
        result.addItemResult(itemKey, success, errorMessage);
        ctx.reportItemComplete(itemKey, success, errorMessage);
    }
}
