package com.gjl.music.module.song.delete;

import com.gjl.music.config.ConfigChangedEvent;
import com.gjl.music.config.ConfigService;
import com.gjl.music.infra.util.AudioFileUtils;
import com.gjl.music.mapper.SongMapper;
import com.gjl.music.editor.mapper.SongManageMapper;
import com.gjl.music.module.FailurePolicy;
import com.gjl.music.infra.pipeline.NodeContext;
import com.gjl.music.infra.pipeline.NodeHandler;
import com.gjl.music.infra.pipeline.NodeResult;
import com.gjl.music.search.EntityChangeEvent;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 删除模块 —— 支持三种删除模式。
 *
 * <p>模式由 options.mode 控制：
 * <ul>
 *   <li>{@code empty-folders} —— 递归清理空文件夹（删后父文件夹变空则继续删）</li>
 *   <li>{@code non-music-folders} —— 删除不包含音乐文件的文件夹及其所有内容</li>
 *   <li>{@code selected} —— 删除勾选的文件和文件夹，同步清理数据库记录</li>
 * </ul>
 */
@Slf4j
@Component
public class DeleteModuleImpl implements DeleteModule, NodeHandler {

    private final SongMapper songMapper;
    private final SongManageMapper songManageMapper;
    private final ConfigService configService;
    private final ApplicationEventPublisher eventPublisher;

    /** 热更新：删除批大小 */
    private volatile int batchSize;

    public DeleteModuleImpl(SongMapper songMapper, SongManageMapper songManageMapper,
                             ConfigService configService,
                             ApplicationEventPublisher eventPublisher) {
        this.songMapper = songMapper;
        this.songManageMapper = songManageMapper;
        this.configService = configService;
        this.eventPublisher = eventPublisher;
    }

    @PostConstruct
    void reloadConfig() {
        this.batchSize = configService.getInt("delete.batch_size", 200);
    }

    @EventListener
    public void onConfigChanged(ConfigChangedEvent e) {
        if ("delete.batch_size".equals(e.configKey())) {
            this.batchSize = e.asInt(200);
        }
    }

    @Override
    public String name() {
        return "delete";
    }

    @Override
    public FailurePolicy failurePolicy() {
        return FailurePolicy.SKIP;
    }

    @Override
    public String label() { return "文件删除"; }

    @Override
    public NodeResult execute(NodeContext ctx) throws Exception {
        Map<String, Object> options = ctx.getSlot("options");
        String mode = getMode(options);

        log.info("删除模块启动，模式: {}", mode);

        return switch (mode) {
            case "empty-folders" -> executeEmptyFolders(ctx);
            case "non-music-folders" -> executeNonMusicFolders(ctx);
            case "selected" -> executeSelected(ctx);
            default -> {
                log.warn("未知删除模式: {}，跳过", mode);
                yield new NodeResult();
            }
        };
    }

    // ── 模式 1: 删除空文件夹（递归清理） ──

    private NodeResult executeEmptyFolders(NodeContext ctx) {
        NodeResult result = new NodeResult();
        List<Path> roots = resolveRootDirs(ctx);
        if (roots.isEmpty()) {
            log.warn("未指定扫描目录，跳过空文件夹清理");
            return result;
        }

        int totalDeleted = 0;
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                log.debug("跳过非目录路径: {}", root);
                continue;
            }
            totalDeleted += cleanEmptyFolders(root, ctx, result);
        }

        result.addOutput("deletedEmptyFolders", totalDeleted);
        log.info("空文件夹清理完成，共删除 {} 个空文件夹", totalDeleted);
        return result;
    }

    /** 递归清理 root 下的所有空文件夹，返回删除数量 */
    private int cleanEmptyFolders(Path root, NodeContext ctx, NodeResult result) {
        // 收集所有子目录，按深度降序排列（从最深层开始删）
        List<Path> allDirs = collectDirectories(root);
        allDirs.sort(Comparator.comparingInt((Path p) -> p.getNameCount()).reversed());

        int deleted = 0;
        for (Path dir : allDirs) {
            ctx.checkPause();
            if (ctx.isCancelled()) break;

            if (isDirectoryEmpty(dir)) {
                try {
                    Files.delete(dir);
                    deleted++;
                    log.debug("已删除空文件夹: {}", dir);
                    recordResult(ctx, result, dir.toString(), true, null);
                } catch (IOException e) {
                    log.warn("删除空文件夹失败: {} — {}", dir, e.getMessage());
                    recordResult(ctx, result, dir.toString(), false, "删除失败: " + e.getMessage());
                }
            }
        }

        // 检查根目录本身是否变空
        if (isDirectoryEmpty(root)) {
            try {
                Files.delete(root);
                deleted++;
                log.debug("已删除根目录（已变空）: {}", root);
                recordResult(ctx, result, root.toString(), true, null);
            } catch (IOException e) {
                log.warn("删除根目录失败: {} — {}", root, e.getMessage());
            }
        }

        return deleted;
    }

    // ── 模式 2: 删除无音乐的文件夹 ──

    private NodeResult executeNonMusicFolders(NodeContext ctx) {
        NodeResult result = new NodeResult();
        List<Path> roots = resolveRootDirs(ctx);
        if (roots.isEmpty()) {
            log.warn("未指定扫描目录，跳过无音乐文件夹清理");
            return result;
        }

        int totalFolders = 0;
        int totalFiles = 0;
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                log.debug("跳过非目录路径: {}", root);
                continue;
            }
            var counts = cleanNonMusicFolders(root, ctx, result);
            totalFolders += counts.folders;
            totalFiles += counts.files;
        }

        result.addOutput("deletedFolders", totalFolders);
        result.addOutput("deletedFiles", totalFiles);
        log.info("无音乐文件夹清理完成，共删除 {} 个文件夹（含 {} 个文件）", totalFolders, totalFiles);
        return result;
    }

    /** 删除 root 下所有无音乐的文件夹 */
    private DeleteCounts cleanNonMusicFolders(Path root, NodeContext ctx, NodeResult result) {
        List<Path> allDirs = collectDirectories(root);
        // 按深度降序（先处理深层子目录）
        allDirs.sort(Comparator.comparingInt((Path p) -> p.getNameCount()).reversed());

        int deletedFolders = 0;
        int deletedFiles = 0;
        Set<Path> deletedPaths = new HashSet<>();

        for (Path dir : allDirs) {
            ctx.checkPause();
            if (ctx.isCancelled()) break;
            // 已因父目录被删而移除，跳过
            if (isUnderDeleted(dir, deletedPaths)) continue;

            if (!containsMusicFile(dir)) {
                // 统计并删除该目录
                long fileCount = countFiles(dir);
                try {
                    deleteDirectoryRecursively(dir);
                    deletedPaths.add(dir);
                    deletedFolders++;
                    deletedFiles += (int) fileCount;
                    log.debug("已删除无音乐文件夹: {} (含 {} 个文件)", dir, fileCount);
                    recordResult(ctx, result, dir.toString(), true,
                            "删除文件夹，含 " + fileCount + " 个文件");
                } catch (IOException e) {
                    log.warn("删除无音乐文件夹失败: {} — {}", dir, e.getMessage());
                    recordResult(ctx, result, dir.toString(), false, "删除失败: " + e.getMessage());
                }
            }
        }

        // 检查根目录本身
        if (!isUnderDeleted(root, deletedPaths) && !containsMusicFile(root)) {
            long fileCount = countFiles(root);
            try {
                deleteDirectoryRecursively(root);
                deletedFolders++;
                deletedFiles += (int) fileCount;
                log.debug("已删除无音乐根目录: {} (含 {} 个文件)", root, fileCount);
                recordResult(ctx, result, root.toString(), true,
                        "删除根目录，含 " + fileCount + " 个文件");
            } catch (IOException e) {
                log.warn("删除无音乐根目录失败: {} — {}", root, e.getMessage());
                recordResult(ctx, result, root.toString(), false, "删除失败: " + e.getMessage());
            }
        }

        return new DeleteCounts(deletedFolders, deletedFiles);
    }

    // ── 模式 3: 删除勾选的文件和文件夹 ──

    private NodeResult executeSelected(NodeContext ctx) throws Exception {
        NodeResult result = new NodeResult();
        Path[] inputPaths = ctx.getSlot("input.paths");
        if (inputPaths == null || inputPaths.length == 0) {
            log.warn("未选择要删除的文件或文件夹");
            return result;
        }

        // 1. 展开所有路径，收集待删除的文件和目录
        List<Path> allFiles = new ArrayList<>();
        List<Path> dirsToRemove = new ArrayList<>();

        for (Path p : inputPaths) {
            ctx.checkPause();
            if (Files.isDirectory(p)) {
                dirsToRemove.add(p);
                allFiles.addAll(collectAllFiles(p));
            } else if (Files.isRegularFile(p)) {
                allFiles.add(p);
            } else {
                log.debug("路径不存在，跳过: {}", p);
            }
        }

        if (allFiles.isEmpty() && dirsToRemove.isEmpty()) {
            log.info("没有可供删除的有效路径");
            return result;
        }

        log.info("待处理: {} 个文件，{} 个目录", allFiles.size(), dirsToRemove.size());

        // 2. 分批查询数据库中的 songId
        List<String> allPaths = allFiles.stream()
                .map(p -> p.toAbsolutePath().normalize().toString())
                .collect(Collectors.toList());

        Map<String, Long> pathToId = new HashMap<>();
        for (int i = 0; i < allPaths.size(); i += batchSize) {
            ctx.checkPause();
            int end = Math.min(i + batchSize, allPaths.size());
            List<Map<String, Object>> rows =
                    songMapper.selectSongIdsByFilePaths(allPaths.subList(i, end));
            if (rows != null) {
                for (Map<String, Object> row : rows) {
                    String fp = String.valueOf(row.get("FILE_PATH"));
                    Object idObj = row.get("ID");
                    if (idObj instanceof Number n) {
                        pathToId.put(normalize(fp), n.longValue());
                    }
                }
            }
        }

        // 3. 删除文件 + 收集需要清理 DB 的 songId
        List<Long> songIdsToDelete = new ArrayList<>();
        int deletedFiles = 0;
        int failedFiles = 0;

        for (Path file : allFiles) {
            ctx.checkPause();
            if (ctx.isCancelled()) break;

            String absPath = file.toAbsolutePath().normalize().toString();
            try {
                boolean existed = Files.deleteIfExists(file);
                if (existed) {
                    deletedFiles++;
                }
                Long songId = pathToId.get(absPath);
                if (songId != null) {
                    songIdsToDelete.add(songId);
                }
                recordResult(ctx, result, absPath, true, null);
            } catch (IOException e) {
                failedFiles++;
                log.warn("删除文件失败: {} — {}", absPath, e.getMessage());
                recordResult(ctx, result, absPath, false, "删除失败: " + e.getMessage());
            }
        }

        // 4. 批量清理数据库记录（先收集受影响的专辑/艺术家用于后续计数刷新）
        int deletedRecords = 0;
        List<Long> affectedAlbumIds = new ArrayList<>();
        List<Long> affectedArtistIds = new ArrayList<>();
        if (!songIdsToDelete.isEmpty()) {
            affectedAlbumIds = songMapper.findAlbumIdsBySongIds(songIdsToDelete);
            affectedArtistIds = songMapper.findArtistIdsBySongIds(songIdsToDelete);
            for (int i = 0; i < songIdsToDelete.size(); i += batchSize) {
                int end = Math.min(i + batchSize, songIdsToDelete.size());
                List<Long> batch = songIdsToDelete.subList(i, end);
                songManageMapper.deleteSongArtistsBySongIds(batch);
                songManageMapper.deleteSongStylesBySongIds(batch);
                songManageMapper.deleteLyricsBySongIds(batch);
                songManageMapper.deleteSongsByIds(batch);
                deletedRecords += batch.size();
            }
            log.info("已清理 {} 条数据库记录", deletedRecords);

            // 刷新受影响专辑/艺术家的冗余计数
            if (!affectedAlbumIds.isEmpty()) {
                songMapper.updateAlbumSongCounts(affectedAlbumIds);
            }
            if (!affectedArtistIds.isEmpty()) {
                songMapper.updateArtistAlbumCounts(affectedArtistIds);
                songMapper.updateArtistSongCounts(affectedArtistIds);
            }

            // ★ 发布索引删除事件
            for (Long songId : songIdsToDelete) {
                eventPublisher.publishEvent(EntityChangeEvent.songDeleted(songId));
            }
            log.debug("已发布 {} 个歌曲删除事件", songIdsToDelete.size());
        }

        // 5. 删除被清空的目录
        int deletedDirs = 0;
        for (Path dir : dirsToRemove) {
            ctx.checkPause();
            if (ctx.isCancelled()) break;
            try {
                if (Files.exists(dir)) {
                    deleteDirectoryRecursively(dir);
                    deletedDirs++;
                    log.debug("已删除目录: {}", dir);
                }
            } catch (IOException e) {
                log.warn("删除目录失败: {} — {}", dir, e.getMessage());
            }
        }

        result.addOutput("deletedRecords", deletedRecords);
        result.addOutput("deletedFiles", deletedFiles);
        result.addOutput("deletedDirs", deletedDirs);
        result.addOutput("failedFiles", failedFiles);
        log.info("选中删除完成: {} 条DB记录, {} 个文件, {} 个目录, {} 个失败",
                deletedRecords, deletedFiles, deletedDirs, failedFiles);
        return result;
    }

    // ── 辅助方法 ──

    /** 从 options 中解析删除模式 */
    @SuppressWarnings("unchecked")
    private static String getMode(Map<String, Object> options) {
        if (options == null) return "selected";
        // 模式可能在 options.mode 或 options.delete.mode
        Object mode = options.get("mode");
        if (mode instanceof String s && !s.isBlank()) return s;
        Object delete = options.get("delete");
        if (delete instanceof Map<?, ?> dm) {
            Object dmMode = dm.get("mode");
            if (dmMode instanceof String s && !s.isBlank()) return s;
        }
        return "selected";
    }

    /** 解析扫描根目录 */
    private static List<Path> resolveRootDirs(NodeContext ctx) {
        Path[] inputPaths = ctx.getSlot("input.paths");
        if (inputPaths == null || inputPaths.length == 0) {
            return List.of();
        }
        return Arrays.stream(inputPaths)
                .filter(Files::isDirectory)
                .collect(Collectors.toList());
    }

    /** 递归收集目录下所有子目录（不含自身） */
    private static List<Path> collectDirectories(Path root) {
        try (Stream<Path> stream = Files.walk(root)) {
            return stream
                    .filter(Files::isDirectory)
                    .filter(p -> !p.equals(root))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.warn("遍历目录失败: {} — {}", root, e.getMessage());
            return List.of();
        }
    }

    /** 递归收集目录下所有文件 */
    private static List<Path> collectAllFiles(Path root) {
        try (Stream<Path> stream = Files.walk(root)) {
            return stream
                    .filter(Files::isRegularFile)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.warn("遍历目录文件失败: {} — {}", root, e.getMessage());
            return List.of();
        }
    }

    /** 检查目录是否为空（无文件、无子目录） */
    private static boolean isDirectoryEmpty(Path dir) {
        try (Stream<Path> entries = Files.list(dir)) {
            return entries.findAny().isEmpty();
        } catch (IOException e) {
            log.debug("检查目录是否为空失败: {} — {}", dir, e.getMessage());
            return false;
        }
    }

    /** 递归检查目录下是否包含音乐文件 */
    private static boolean containsMusicFile(Path dir) {
        try (Stream<Path> stream = Files.walk(dir)) {
            return stream.anyMatch(AudioFileUtils::isAudioFile);
        } catch (IOException e) {
            log.debug("检查音乐文件失败: {} — {}", dir, e.getMessage());
            return false;
        }
    }

    /** 统计目录下文件数量 */
    private static long countFiles(Path dir) {
        try (Stream<Path> stream = Files.walk(dir)) {
            return stream.filter(Files::isRegularFile).count();
        } catch (IOException e) {
            return 0;
        }
    }

    /** 递归删除目录及其所有内容 */
    private static void deleteDirectoryRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (Stream<Path> stream = Files.walk(dir)) {
            List<Path> sorted = stream
                    .sorted(Comparator.reverseOrder())
                    .collect(Collectors.toList());
            for (Path p : sorted) {
                Files.deleteIfExists(p);
            }
        }
    }

    /** 检查 path 是否在已删除目录之下 */
    private static boolean isUnderDeleted(Path path, Set<Path> deletedPaths) {
        for (Path deleted : deletedPaths) {
            if (path.startsWith(deleted)) return true;
        }
        return false;
    }

    /** 路径规范化 */
    private static String normalize(String raw) {
        return Path.of(raw).toAbsolutePath().normalize().toString();
    }

    /** 同时记录到 NodeResult 并通过 NodeContext 实时上报进度 */
    private static void recordResult(NodeContext ctx, NodeResult result,
                                      String itemKey, boolean success, String errorMessage) {
        result.addItemResult(itemKey, success, errorMessage);
        ctx.reportItemComplete(itemKey, success, errorMessage);
    }

    /** 删除计数 */
    private record DeleteCounts(int folders, int files) {}
}
