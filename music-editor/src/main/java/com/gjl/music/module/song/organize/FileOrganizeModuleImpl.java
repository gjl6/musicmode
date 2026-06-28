package com.gjl.music.module.song.organize;

import com.gjl.music.config.ConfigChangedEvent;
import com.gjl.music.config.ConfigService;
import com.gjl.music.exception.ModuleException;
import com.gjl.music.editor.mapper.SongManageMapper;
import com.gjl.music.model.*;
import com.gjl.music.module.FailurePolicy;
import com.gjl.music.module.song.support.MetadataGapFillingModule;
import com.gjl.music.module.song.support.SkipException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.nio.file.*;
import java.util.*;

/**
 * 文件整理模块 —— 按元数据层级重组文件目录结构。
 *
 * <p>继承 {@link MetadataGapFillingModule} 复用 DB 缓存查询、缺失解析、
 * 批量并行处理等通用流程。在 {@link #processItem} 中执行文件移动/复制。
 *
 * <p>配置格式（通过 options.organize 传入）：
 * <pre>{@code
 * {
 *   mode: "move" | "copy",     // 默认 "move"
 *   targetRoot: "/path/to/root", // 可选，为空时使用源文件所在目录
 *   levels: [
 *     { field: "artist" },
 *     { field: "album" },
 *     { field: "trackNumber" },
 *     { field: "title" }
 *   ]
 * }
 * }</pre>
 */
@Slf4j
@Component
public class FileOrganizeModuleImpl extends MetadataGapFillingModule implements FileOrganizeModule {

    @Value("${music.root-dir}")
    private String musicRootDir;

    private final ConfigService configService;

    private String mode = MODE_MOVE;
    private Path targetRoot;
    private String browsePath;
    private List<OrganizeField> levels = List.of();

    public FileOrganizeModuleImpl(SongManageMapper songManageMapper, ConfigService configService) {
        super(songManageMapper);
        this.configService = configService;
    }

    @PostConstruct
    void reloadConfig() {
        OrganizePathBuilder.maxSegmentLength = configService.getInt("organize.max_segment_length", 200);
        OrganizePathBuilder.maxConflictRetries = configService.getInt("organize.max_conflict_retries", 1000);
    }

    @EventListener
    public void onConfigChanged(ConfigChangedEvent e) {
        switch (e.configKey()) {
            case "organize.max_segment_length" ->
                    OrganizePathBuilder.maxSegmentLength = e.asInt(200);
            case "organize.max_conflict_retries" ->
                    OrganizePathBuilder.maxConflictRetries = e.asInt(1000);
        }
    }

    @Override
    public String name() {
        return "organize";
    }

    @Override
    public FailurePolicy failurePolicy() {
        return FailurePolicy.SKIP;
    }

    @Override public String label() { return "文件整理"; }

    @Override
    public List<Map<String, Object>> configSchema() {
        return List.of(
            Map.of("key", "mode", "label", "操作模式", "type", "select",
                "default", "move",
                "options", List.of(
                    Map.of("value", "move", "label", "移动文件"),
                    Map.of("value", "copy", "label", "复制文件")
                )),
            Map.of("key", "targetRoot", "label", "目标根目录", "type", "string",
                "default", "", "placeholder", "留空使用源文件所在目录"),
            Map.of("key", "pattern", "label", "目录层级", "type", "string",
                "default", "artist/album",
                "placeholder", "如: artist/album/title，分隔符 /")
        );
    }

    /** 不需要写标签（仅改变文件位置/复制文件，标签内容未变） */
    @Override
    protected boolean shouldWriteTags() {
        return false;
    }

    // ── 配置 ──

    @Override
    @SuppressWarnings("unchecked")
    public void configure(Map<String, Object> options) {
        if (options == null) return;

        // 解析 mode
        Object modeVal = options.get(KEY_MODE);
        if (modeVal instanceof String s && MODE_COPY.equalsIgnoreCase(s)) {
            this.mode = MODE_COPY;
        } else {
            this.mode = MODE_MOVE;
        }

        // 解析 targetRoot（可选绝对/相对路径）
        if (options.containsKey(KEY_TARGET_ROOT)) {
            Object root = options.get(KEY_TARGET_ROOT);
            if (root instanceof String s && !s.isBlank()) {
                this.targetRoot = Path.of(s);
            } else {
                this.targetRoot = null;
            }
        }

        // 解析 browsePath（当前浏览目录，targetRoot 为空时的后备）
        Object bp = options.get("browsePath");
        if (bp instanceof String s && !s.isBlank()) {
            this.browsePath = s;
        } else {
            this.browsePath = null;
        }

        // 解析 levels
        if (options.get(KEY_LEVELS) instanceof List<?> levelList) {
            List<OrganizeField> parsed = new ArrayList<>();
            for (Object item : levelList) {
                if (item instanceof Map<?, ?> map) {
                    Object field = map.get(KEY_FIELD);
                    if (field instanceof String s) {
                        OrganizeField f = OrganizeField.fromConfigKey(s);
                        if (f != null) {
                            parsed.add(f);
                        } else {
                            log.warn("file-organize: 未知字段 '{}'，已跳过", s);
                        }
                    }
                }
            }
            this.levels = Collections.unmodifiableList(parsed);
        }

        // 兼容 pattern 字符串（如 "artist/album"）→ levels
        if (levels.isEmpty() && options.get("pattern") instanceof String pattern && !pattern.isBlank()) {
            List<OrganizeField> parsed = new ArrayList<>();
            for (String seg : pattern.split("/")) {
                String fieldName = seg.strip();
                if (fieldName.isEmpty()) continue;
                OrganizeField f = OrganizeField.fromConfigKey(fieldName);
                if (f != null) {
                    parsed.add(f);
                } else {
                    log.warn("file-organize: pattern 中未知字段 '{}'，已跳过", fieldName);
                }
            }
            if (!parsed.isEmpty()) {
                this.levels = Collections.unmodifiableList(parsed);
            }
        }

        log.info("organize 配置: mode={}, targetRoot={}, levels={}",
                mode, targetRoot,
                levels.stream().map(OrganizeField::getConfigKey).toList());
    }

    // ── 核心逻辑 ──

    @Override
    protected MusicMetadata processItem(MusicMetadata meta) {
        if (meta == null || meta.getImmutableSongs().isEmpty()) {
            throw new ModuleException(name(), "输入元数据为空");
        }
        if (levels.isEmpty()) {
            log.debug("file-organize: 未配置层级，跳过");
            return null;
        }

        Song oldSong = meta.getImmutableSongs().get(0);
        Path sourcePath = Path.of(oldSong.getFilePath());

        // 1. 验证源文件存在
        if (!Files.isRegularFile(sourcePath)) {
            throw new ModuleException(name(), "源文件不存在: " + oldSong.getFilePath());
        }

        // 2. 确定目标根目录（优先级：targetRoot > browsePath > 源文件父目录）
        Path effectiveRoot;
        if (targetRoot != null) {
            effectiveRoot = targetRoot.toAbsolutePath();
        } else if (browsePath != null) {
            // browsePath 是相对于音乐根目录的路径
            Path musicRoot = Path.of(musicRootDir);
            effectiveRoot = musicRoot.resolve(browsePath.startsWith("/") ? browsePath.substring(1) : browsePath);
        } else {
            effectiveRoot = sourcePath.getParent();
        }
        if (effectiveRoot == null) effectiveRoot = Path.of(".");

        // 3. 计算目标路径
        Path targetPath = OrganizePathBuilder.buildTargetPath(
                sourcePath, levels, effectiveRoot, meta);

        // 4. 如果目标路径与源路径相同，跳过
        if (targetPath.normalize().equals(sourcePath.normalize())) {
            log.debug("文件已在目标位置，跳过: {}", sourcePath.getFileName());
            return null;
        }

        // 5. 创建目标父目录
        Path targetParent = targetPath.getParent();
        if (targetParent != null) {
            try {
                Files.createDirectories(targetParent);
            } catch (Exception e) {
                throw new ModuleException(name(), "无法创建目标目录: " + targetParent, e);
            }
        }

        // 6. 执行移动或复制（虚拟线程下阻塞 I/O 自动释放 carrier thread）
        MusicMetadata resultMeta = meta;
        try {
            if (MODE_COPY.equals(mode)) {
                log.info("复制: {} → {}", sourcePath, targetPath);
                Files.copy(sourcePath, targetPath, StandardCopyOption.COPY_ATTRIBUTES);
                // 复制模式：原文件不变，返回原 metadata 记录成功
            } else {
                log.info("移动: {} → {}", sourcePath, targetPath);
                Path sourceParent = sourcePath.getParent();
                Files.move(sourcePath, targetPath);
                // 清理源文件留下的空目录
                cleanupEmptyDirs(sourceParent, effectiveRoot);
                // 移动模式：直接更新 DB 路径（MERGE 按 file_path 匹配，先改后回）
                songManageMapper.updateSongFilePath(
                        oldSong.getFilePath(),
                        targetPath.toString(),
                        targetPath.getFileName().toString());
                log.debug("DB 路径已更新: {} → {}", oldSong.getFilePath(), targetPath);
                // 构建含新路径的 metadata，框架 db-operator MERGE 会匹配已更新的行（新路径→无重复）
                resultMeta = buildResultMetadata(meta, targetPath);
            }
        } catch (FileAlreadyExistsException e) {
            throw new ModuleException(name(), "目标文件已存在: " + targetPath);
        } catch (ModuleException e) {
            throw e;
        } catch (Exception e) {
            throw new ModuleException(name(), "文件操作失败: " + e.getMessage(), e);
        }

        // 返回非 null metadata 让框架记录成功（writer 已被 shouldWriteTags=false 跳过）
        return resultMeta;
    }

    /** 构建含新文件路径的 MusicMetadata，用于框架记录成功 */
    private MusicMetadata buildResultMetadata(MusicMetadata oldMeta, Path newPath) {
        Song oldSong = oldMeta.getImmutableSongs().getFirst();
        Song updatedSong = oldSong.toBuilder()
                .filePath(newPath.toString())
                .fileName(newPath.getFileName().toString())
                .build();
        MusicMetadata result = new MusicMetadata();
        result.addSong(updatedSong);
        for (Album a : oldMeta.getImmutableAlbums()) result.addAlbum(a);
        for (Artist a : oldMeta.getImmutableArtists()) result.addArtist(a);
        for (Style s : oldMeta.getImmutableStyles()) result.addStyle(s);
        for (Lyric l : oldMeta.getImmutableLyrics()) result.addLyric(l);
        return result;
    }

    /**
     * 递归删除空目录，从 {@code dir} 向上直到 {@code stopAt}（不含）。
     * 仅删除空目录，非空或非目录则停止。
     */
    private void cleanupEmptyDirs(Path dir, Path stopAt) {
        Path current = dir;
        while (current != null && !current.equals(stopAt)) {
            try {
                if (!Files.isDirectory(current)) break;
                try (var stream = Files.list(current)) {
                    if (stream.findAny().isPresent()) break; // 非空，停止
                }
                Files.deleteIfExists(current);
                log.debug("清理空目录: {}", current);
                current = current.getParent();
            } catch (Exception e) {
                log.debug("清理目录失败（跳过）: {} - {}", current, e.getMessage());
                break;
            }
        }
    }
}
