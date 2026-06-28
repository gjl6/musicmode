package com.gjl.music.watch;

import com.gjl.music.infra.util.AudioFileUtils;
import com.gjl.music.infra.util.PathUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Optional;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 自动任务执行器 — 执行单次扫描（DIR_SCAN / FILE_SCAN），检测文件变更并回调处理器。
 *
 * <h3>DIR_SCAN（目录级）</h3>
 * walkFileTree → 目录 mtime 跳级 → 收集变更目录下的音频文件 → 查 song 表对比 mtime → 回调 handler。
 *
 * <h3>FILE_SCAN（文件级）</h3>
 * walkFileTree → 收集所有音频文件 → 批量查 song 表对比 mtime → 回调 handler。
 *
 * <p>位于 common，通过 {@link SongDbAccessor} 和 {@link FileChangeHandler} 解耦模块特有逻辑。
 */
@Slf4j
@Component
public class AutoTaskRunner {

    private final SongDbAccessor dbAccessor; // 可选，无 bean 时为 null
    private final FileChangeHandler changeHandler; // 可选，无 bean 时为 null
    private final Path rootDir;

    public AutoTaskRunner(
            Optional<SongDbAccessor> dbAccessor,
            Optional<FileChangeHandler> changeHandler,
            @Value("${music.root-dir}") String rootDir) {
        this.dbAccessor = dbAccessor.orElse(null);
        this.changeHandler = changeHandler.orElse(null);
        this.rootDir = Path.of(rootDir).toAbsolutePath().normalize();
    }

    // ═══════════════════════════════════════════════════════════════
    // DIR_SCAN
    // ═══════════════════════════════════════════════════════════════

    /**
     * 执行目录级扫描。
     *
     * @param task          任务配置
     * @param dirTimestamps 目录时间戳缓存（dirPath → mtime），由调度器持有
     * @return 扫描结果，首次扫描或无变更时返回 {@code null}
     */
    public ScanResult runDirScan(TaskInfo task, ConcurrentHashMap<String, Long> dirTimestamps) {
        Path scanRoot = resolveScanRoot(task.getWatchPath());
        if (scanRoot == null) return null;

        long t0 = System.currentTimeMillis();
        List<Path> newFiles = new ArrayList<>();
        List<Path> modFiles = new ArrayList<>();
        List<Long> delSongIds = new ArrayList<>();
        List<Path> delPaths = new ArrayList<>();
        AtomicInteger dirsVisited = new AtomicInteger();
        AtomicInteger dirsSkipped = new AtomicInteger();

        boolean isFirstScan = dirTimestamps.isEmpty();

        try {
            Map<String, List<Path>> changedDirFiles = new LinkedHashMap<>();

            Files.walkFileTree(scanRoot, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    dirsVisited.incrementAndGet();
                    String key = dir.toString();
                    Long snapshotTs = dirTimestamps.get(key);
                    long currentTs = attrs.lastModifiedTime().toMillis();

                    if (!isFirstScan && snapshotTs != null && currentTs == snapshotTs) {
                        dirsSkipped.incrementAndGet();
                        return FileVisitResult.SKIP_SUBTREE;
                    }

                    List<Path> audioFiles = collectAudioFilesRecursively(dir);
                    if (!audioFiles.isEmpty()) {
                        changedDirFiles.put(key, audioFiles);
                    }
                    dirTimestamps.put(key, currentTs);
                    return FileVisitResult.SKIP_SUBTREE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });

            if (isFirstScan) {
                log.info("DIR_SCAN '{}' 首次扫描: {} 目录, 建立基线并处理已有文件", task.getName(), dirsVisited.get());
            }

            if (!changedDirFiles.isEmpty()) {
                Map<String, Path> uniqueFiles = new LinkedHashMap<>();
                for (List<Path> files : changedDirFiles.values()) {
                    for (Path f : files) {
                        uniqueFiles.putIfAbsent(f.toAbsolutePath().toString(), f);
                    }
                }

                String prefix = scanRoot.toString();
                if (!prefix.endsWith(File.separator)) prefix += File.separator;

                if (dbAccessor == null) {
                    log.warn("未配置 SongDbAccessor，DIR_SCAN 跳过 DB 比对");
                    return null;
                }
                Map<String, long[]> dbMap = dbAccessor.queryUnderRoot(
                        PathUtils.escapeMySqlLike(prefix));

                for (var entry : uniqueFiles.entrySet()) {
                    String absPath = entry.getKey();
                    Path file = entry.getValue();
                    long[] dbInfo = dbMap.get(absPath);
                    if (dbInfo == null) {
                        newFiles.add(file);
                    } else {
                        long dbMtime = dbInfo[1];
                        long diskMtime = file.toFile().lastModified();
                        if (diskMtime != dbMtime) {
                            modFiles.add(file);
                        }
                    }
                }

                // 删除检测
                Set<String> diskPaths = new HashSet<>();
                for (List<Path> files : changedDirFiles.values()) {
                    for (Path f : files) diskPaths.add(f.toAbsolutePath().toString());
                }
                for (var dbEntry : dbMap.entrySet()) {
                    String dbPath = dbEntry.getKey();
                    boolean inChangedDir = changedDirFiles.keySet().stream()
                            .anyMatch(dir -> dbPath.startsWith(dir));
                    if (inChangedDir && !diskPaths.contains(dbPath)) {
                        delSongIds.add(dbEntry.getValue()[0]);
                        delPaths.add(Path.of(dbPath));
                    }
                }
            }

        } catch (IOException e) {
            log.error("DIR_SCAN '{}' 遍历失败", task.getName(), e);
            return null;
        }

        long elapsed = System.currentTimeMillis() - t0;

        ScanResult result = new ScanResult(newFiles, modFiles, delSongIds, delPaths);
        if (!result.isEmpty()) {
            log.info("DIR_SCAN '{}' 完成: {}ms, 访问{}目录(跳过{}), 新增{} 修改{} 删除{}",
                    task.getName(), elapsed, dirsVisited.get(), dirsSkipped.get(),
                    newFiles.size(), modFiles.size(), delSongIds.size());
            processChanges(task, result);
        } else if (log.isDebugEnabled()) {
            log.debug("DIR_SCAN '{}' 完成: {}ms, 无变更", task.getName(), elapsed);
        }
        return result;
    }

    // ═══════════════════════════════════════════════════════════════
    // FILE_SCAN
    // ═══════════════════════════════════════════════════════════════

    private static final int FILE_BATCH_SIZE = 500;
    private static final int DELETE_PAGE_SIZE = 2000;

    /**
     * 执行文件级扫描。分批处理避免全量加载到内存。
     */
    public ScanResult runFileScan(TaskInfo task) {
        Path scanRoot = resolveScanRoot(task.getWatchPath());
        if (scanRoot == null) return null;

        long t0 = System.currentTimeMillis();
        List<Path> newFiles = new ArrayList<>();
        List<Path> modFiles = new ArrayList<>();
        List<Long> delSongIds = new ArrayList<>();
        List<Path> delPaths = new ArrayList<>();

        List<Map.Entry<String, Long>> batch = new ArrayList<>(FILE_BATCH_SIZE);
        AtomicInteger fileCount = new AtomicInteger();

        try {
            Files.walkFileTree(scanRoot, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (AudioFileUtils.isAudioFile(file.toFile())) {
                        batch.add(Map.entry(file.toAbsolutePath().toString(),
                                attrs.lastModifiedTime().toMillis()));
                        if (batch.size() >= FILE_BATCH_SIZE) {
                            processFileBatch(batch, newFiles, modFiles);
                            fileCount.addAndGet(batch.size());
                            batch.clear();
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });

            if (!batch.isEmpty()) {
                processFileBatch(batch, newFiles, modFiles);
                fileCount.addAndGet(batch.size());
            }

            String prefix = scanRoot.toString();
            if (!prefix.endsWith(File.separator)) prefix += File.separator;
            detectDeletedFiles(prefix, delSongIds, delPaths);

        } catch (IOException e) {
            log.error("FILE_SCAN '{}' 遍历失败", task.getName(), e);
            return null;
        }

        long elapsed = System.currentTimeMillis() - t0;
        ScanResult result = new ScanResult(newFiles, modFiles, delSongIds, delPaths);
        if (!result.isEmpty()) {
            log.info("FILE_SCAN '{}' 完成: {}ms, {}文件, 新增{} 修改{} 删除{}",
                    task.getName(), elapsed, fileCount.get(),
                    newFiles.size(), modFiles.size(), delSongIds.size());
            processChanges(task, result);
        } else {
            log.debug("FILE_SCAN '{}' 完成: {}ms, {}文件, 无变更", task.getName(), elapsed, fileCount.get());
        }
        return result;
    }

    // ═══════════════════════════════════════════════════════════════
    // 批次处理
    // ═══════════════════════════════════════════════════════════════

    private void processFileBatch(List<Map.Entry<String, Long>> batch,
                                   List<Path> newFiles, List<Path> modFiles) {
        List<String> batchPaths = new ArrayList<>(batch.size());
        Map<String, Long> diskMtimes = new HashMap<>(batch.size());
        for (var entry : batch) {
            batchPaths.add(entry.getKey());
            diskMtimes.put(entry.getKey(), entry.getValue());
        }

        Map<String, long[]> dbMap;
        if (dbAccessor == null) return;
        try {
            dbMap = dbAccessor.queryByPaths(batchPaths);
        } catch (Exception e) {
            log.warn("分批查询 DB 失败: {}", e.getMessage());
            return;
        }

        for (var entry : batch) {
            String absPath = entry.getKey();
            Long diskMtime = entry.getValue();
            long[] dbInfo = dbMap.get(absPath);
            if (dbInfo == null) {
                newFiles.add(Path.of(absPath));
            } else if (diskMtime != dbInfo[1]) {
                modFiles.add(Path.of(absPath));
            }
        }
    }

    private void detectDeletedFiles(String prefix, List<Long> delSongIds, List<Path> delPaths) {
        if (dbAccessor == null) return;
        int offset = 0;
        int totalChecked = 0;
        String escapedPrefix = PathUtils.escapeMySqlLike(prefix);
        while (true) {
            List<Map<String, Object>> page;
            try {
                page = dbAccessor.queryPaged(escapedPrefix, offset, DELETE_PAGE_SIZE);
            } catch (Exception e) {
                log.warn("分页查询 DB 失败 (offset={}): {}", offset, e.getMessage());
                break;
            }
            if (page == null || page.isEmpty()) break;

            for (Map<String, Object> row : page) {
                String path = String.valueOf(row.get("FILE_PATH"));
                if (!Files.exists(Path.of(path))) {
                    delSongIds.add(numFromRow(row, "ID"));
                    delPaths.add(Path.of(path));
                }
            }
            totalChecked += page.size();
            if (page.size() < DELETE_PAGE_SIZE) break;
            offset += DELETE_PAGE_SIZE;
        }
        if (totalChecked > 0) {
            log.debug("删除检测完成: {} 条 DB 记录, 发现 {} 个删除", totalChecked, delSongIds.size());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 变更处理
    // ═══════════════════════════════════════════════════════════════

    /**
     * 处理扫描检测到的文件变更：
     * 1. 有新增/修改 → 回调 {@link FileChangeHandler#onChanges}
     * 2. 有删除 → 调用 {@link SongDbAccessor#cascadeDelete}
     */
    void processChanges(TaskInfo task, ScanResult changes) {
        if (changes.isEmpty()) return;

        // 新增 + 修改 → 回调 handler
        List<Path> toProcess = new ArrayList<>();
        if (changes.newFiles() != null) toProcess.addAll(changes.newFiles());
        if (changes.modFiles() != null) toProcess.addAll(changes.modFiles());

        if (!toProcess.isEmpty() && changeHandler != null) {
            log.info("任务 '{}' 检测到 {} 个文件变更，回调 FileChangeHandler",
                    task.getName(), toProcess.size());
            changeHandler.onChanges(task, changes);
        } else if (!toProcess.isEmpty()) {
            log.info("任务 '{}' 检测到 {} 个文件变更，但未配置 FileChangeHandler，跳过",
                    task.getName(), toProcess.size());
        }

        // 删除 → 级联清理
        if (changes.delSongIds() != null && !changes.delSongIds().isEmpty()
                && dbAccessor != null) {
            try {
                dbAccessor.cascadeDelete(changes.delSongIds());
                log.info("任务 '{}' 已清理 {} 个已删除文件的 DB 记录", task.getName(),
                        changes.delSongIds().size());
            } catch (Exception e) {
                log.error("任务 '{}' 清理删除文件失败", task.getName(), e);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 辅助方法
    // ═══════════════════════════════════════════════════════════════

    private Path resolveScanRoot(String watchPath) {
        Path scanRoot;
        if ("/".equals(watchPath) || watchPath == null || watchPath.isBlank()) {
            scanRoot = rootDir;
        } else {
            String relative = watchPath.startsWith("/") ? watchPath.substring(1) : watchPath;
            scanRoot = rootDir.resolve(relative).normalize();
        }
        if (!Files.isDirectory(scanRoot)) {
            log.warn("监控路径不存在或不是目录: {}", scanRoot);
            return null;
        }
        return scanRoot;
    }

    private static List<Path> collectAudioFilesRecursively(Path dir) {
        List<Path> result = new ArrayList<>();
        File[] children = dir.toFile().listFiles();
        if (children == null) return result;
        for (File child : children) {
            if (child.isFile() && AudioFileUtils.isAudioFile(child)) {
                result.add(child.toPath());
            } else if (child.isDirectory()) {
                result.addAll(collectAudioFilesRecursively(child.toPath()));
            }
        }
        return result;
    }

    private static long numFromRow(Map<String, Object> row, String col) {
        Object v = row.get(col);
        return v instanceof Number n ? n.longValue() : 0L;
    }
}
