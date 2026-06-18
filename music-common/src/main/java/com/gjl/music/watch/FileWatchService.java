package com.gjl.music.watch;

import com.gjl.music.infra.util.AudioFileUtils;
import com.gjl.music.mapper.FileWatchSnapshotMapper;
import com.gjl.music.mapper.MusicMapper;
import com.gjl.music.model.FileWatchSnapshot;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;


@Slf4j
@Service
public class FileWatchService implements ApplicationRunner, SmartLifecycle {


    private final FileWatchHandler handler;
    private final MusicMapper musicMapper;
    private final FileWatchSnapshotMapper snapshotMapper;
    private final StringRedisTemplate redis;
    private final Path rootDir;
    private final boolean enabled;
    private final boolean redisEnabled;
    private final int incrementalIntervalSec;
    private final int fullScanIntervalMin;

    private static final String REDIS_KEY = "watch:snapshot";


    private final ConcurrentHashMap<String, Long> dirTimestamps = new ConcurrentHashMap<>();

    private final AtomicBoolean incrementalScanning = new AtomicBoolean(false);

    private final AtomicBoolean fullScanning = new AtomicBoolean(false);

    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(2, r -> {
                Thread t = new Thread(r);
                t.setDaemon(true);
                t.setName("watch-sched");
                return t;
            });

    private volatile boolean running = false;


    public FileWatchService(
            FileWatchHandler handler,
            MusicMapper musicMapper,
            FileWatchSnapshotMapper snapshotMapper,
            @org.springframework.beans.factory.annotation.Autowired(required = false) StringRedisTemplate stringRedisTemplate,
            @Qualifier("virtualThreadExecutor") Executor virtualExecutor,
            @Value("${music.root-dir}") String rootDir,
            @Value("${music.watch.enabled:true}") boolean enabled,
            @Value("${music.watch.incremental-interval-sec:30}") int incrementalIntervalSec,
            @Value("${music.watch.full-scan-interval-min:120}") int fullScanIntervalMin) {
        this.handler = handler;
        this.musicMapper = musicMapper;
        this.snapshotMapper = snapshotMapper;
        this.redis = stringRedisTemplate;
        this.rootDir = Path.of(rootDir).toAbsolutePath().normalize();
        this.enabled = enabled;
        this.redisEnabled = stringRedisTemplate != null;
        this.incrementalIntervalSec = incrementalIntervalSec;
        this.fullScanIntervalMin = fullScanIntervalMin;
    }


    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("文件监控已禁用 (music.watch.enabled=false)");
            return;
        }
        if (!Files.isDirectory(rootDir)) {
            log.warn("监控根目录不存在或不是目录: {}，跳过启动", rootDir);
            return;
        }

        running = true;

                loadSnapshot();

                if (dirTimestamps.isEmpty()) {
            log.info("首次启动，全量扫描建立基线...");
            fullScan();
        }

                scheduler.scheduleWithFixedDelay(
                this::incrementalScan,
                incrementalIntervalSec,
                incrementalIntervalSec,
                TimeUnit.SECONDS);

        scheduler.scheduleWithFixedDelay(
                this::fullScan,
                fullScanIntervalMin,
                fullScanIntervalMin,
                TimeUnit.MINUTES);

        log.info("文件监控已启动: rootDir={}, incremental={}s, full={}min, redis={}",
                rootDir, incrementalIntervalSec, fullScanIntervalMin, redisEnabled);
    }


    @Override
    public int getPhase() { return Integer.MAX_VALUE; }

    @Override
    public void start() {  }

    @Override
    public void stop() { doShutdown(); }

    private final AtomicBoolean shutdownDone = new AtomicBoolean(false);

    private void doShutdown() {
        if (!shutdownDone.compareAndSet(false, true)) return;
        running = false;
        scheduler.shutdownNow();
        syncAllToRedis();
        flushSnapshot();
        log.info("文件监控已停止");
    }

    @PreDestroy
    public void preDestroy() { doShutdown(); }


    void incrementalScan() {
        if (!incrementalScanning.compareAndSet(false, true)) {
            log.debug("上一轮增量扫描尚未完成，跳过");
            return;
        }
        try {
            long t0 = System.currentTimeMillis();
            List<Path> changedDirs = new ArrayList<>();
            AtomicInteger dirsVisited = new AtomicInteger();
            AtomicInteger dirsSkipped = new AtomicInteger();

                        try {
                Files.walkFileTree(rootDir, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        dirsVisited.incrementAndGet();
                        String key = dir.toString();
                        Long snapshotTs = dirTimestamps.get(key);
                        long currentTs = attrs.lastModifiedTime().toMillis();

                        if (snapshotTs != null && currentTs == snapshotTs) {
                            dirsSkipped.incrementAndGet();
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        changedDirs.add(dir);
                        dirTimestamps.put(key, currentTs);
                        syncDirToRedis(key, currentTs);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException exc) {
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                log.error("增量目录遍历失败", e);
                return;
            }

            long t1 = System.currentTimeMillis();

                        int created = 0, modified = 0, deleted = 0;
            List<Path> newFiles = new ArrayList<>();
            List<Path> modFiles = new ArrayList<>();
            List<Path> delPaths = new ArrayList<>();
            List<Long> delSongIds = new ArrayList<>();

            if (!changedDirs.isEmpty()) {
                for (Path dir : changedDirs) {
                    if (Thread.currentThread().isInterrupted()) break;
                    int[] partial = scanDirFiles(dir, newFiles, modFiles, delSongIds, delPaths);
                    created += partial[0];
                    modified += partial[1];
                    deleted += partial[2];
                }
                                flushBatches(newFiles, modFiles, delSongIds, delPaths);
                if (created > 0 || modified > 0 || deleted > 0) {
                    flushSnapshot();
                }
            }

            long elapsed = System.currentTimeMillis() - t0;
            if (!changedDirs.isEmpty() || log.isDebugEnabled()) {
                log.info("增量扫描完成: {}ms, 访问{}个目录(跳过{}个), 变化{}个目录, 新增{} 修改{} 删除{}",
                        elapsed, dirsVisited.get(), dirsSkipped.get(), changedDirs.size(), created, modified, deleted);
            }
        } catch (Exception e) {
            log.error("增量扫描异常", e);
        } finally {
            incrementalScanning.set(false);
        }
    }


    private int[] scanDirFiles(Path dir, List<Path> newFiles, List<Path> modFiles,
                               List<Long> delSongIds, List<Path> delPaths) {
        int created = 0, modified = 0, deleted = 0;

                Map<String, Path> diskFiles = new LinkedHashMap<>();
        File[] children = dir.toFile().listFiles();
        if (children == null) return new int[]{0, 0, 0};

        for (File child : children) {
            if (child.isFile() && AudioFileUtils.isAudioFile(child)) {
                diskFiles.put(child.getAbsolutePath(), child.toPath());
            }
        }

                Map<String, long[]> dbMap = queryDbPathsUnderDir(dir.toString());

                for (var diskEntry : diskFiles.entrySet()) {
            String absPath = diskEntry.getKey();
            Path file = diskEntry.getValue();
            long[] dbInfo = dbMap.get(absPath);

            if (dbInfo == null) {
                log.info("检测到新文件: {}", absPath);
                created++;
                newFiles.add(file);
            } else {
                long dbMtime = dbInfo[1];
                long diskMtime = file.toFile().lastModified();
                if (diskMtime != dbMtime) {
                    log.info("检测到文件修改: {} (DB mtime={}, disk mtime={})", absPath, dbMtime, diskMtime);
                    modified++;
                    modFiles.add(file);
                }
            }
        }

                String dirPrefix = dir.toString();
        if (!dirPrefix.endsWith(File.separator)) dirPrefix += File.separator;
        for (var dbEntry : dbMap.entrySet()) {
            String dbPath = dbEntry.getKey();
            if (dbPath.startsWith(dirPrefix) && !diskFiles.containsKey(dbPath)) {
                log.info("检测到文件删除: {}", dbPath);
                deleted++;
                delSongIds.add(dbEntry.getValue()[0]);
                delPaths.add(Path.of(dbPath));
            }
        }

        return new int[]{created, modified, deleted};
    }


    private void flushBatches(List<Path> newFiles, List<Path> modFiles,
                              List<Long> delSongIds, List<Path> delPaths) {
        if (!newFiles.isEmpty()) handler.onNewFiles(List.copyOf(newFiles));
        if (!modFiles.isEmpty()) handler.onModifiedFiles(List.copyOf(modFiles));
        if (!delSongIds.isEmpty()) {
            handler.onDeletedFiles(List.copyOf(delSongIds), List.copyOf(delPaths));
        }
    }


    void fullScan() {
        if (!fullScanning.compareAndSet(false, true)) {
            log.info("全量扫描已在运行，跳过");
            return;
        }
        try {
            log.info("全量对账开始...");
            long t0 = System.currentTimeMillis();

            Set<String> diskPaths = new HashSet<>();
            Map<String, Long> newDirTimestamps = new LinkedHashMap<>();
            AtomicInteger fileCount = new AtomicInteger();
            AtomicInteger dirCount = new AtomicInteger();

            try {
                Files.walkFileTree(rootDir, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        newDirTimestamps.put(dir.toString(), attrs.lastModifiedTime().toMillis());
                        dirCount.incrementAndGet();
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        if (AudioFileUtils.isAudioFile(file)) {
                            diskPaths.add(file.toAbsolutePath().toString());
                            fileCount.incrementAndGet();
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException exc) {
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                log.error("全量遍历失败", e);
                return;
            }

            long t1 = System.currentTimeMillis();
            log.info("磁盘遍历完成: {}ms, {} 目录, {} 文件", t1 - t0, dirCount.get(), fileCount.get());

                        Map<String, long[]> dbMap = queryAllDbPaths();
            int newFiles = 0, modifiedFiles = 0, goneFiles = 0;
            List<Path> newBatch = new ArrayList<>();
            List<Path> modBatch = new ArrayList<>();
            List<Long> delSongIds = new ArrayList<>();
            List<Path> delPaths = new ArrayList<>();

            for (String diskPath : diskPaths) {
                long[] dbInfo = dbMap.get(diskPath);
                if (dbInfo == null) {
                    newFiles++;
                    newBatch.add(Path.of(diskPath));
                } else {
                    long dbMtime = dbInfo[1];
                    long diskMtime = new File(diskPath).lastModified();
                    if (diskMtime != dbMtime) {
                        log.info("全量对账-修改: {} (DB mtime={}, disk mtime={})", diskPath, dbMtime, diskMtime);
                        modifiedFiles++;
                        modBatch.add(Path.of(diskPath));
                    }
                }
            }

            for (var dbEntry : dbMap.entrySet()) {
                String dbPath = dbEntry.getKey();
                if (!diskPaths.contains(dbPath)) {
                    goneFiles++;
                    delSongIds.add(dbEntry.getValue()[0]);
                    delPaths.add(Path.of(dbPath));
                }
            }

                        flushBatches(newBatch, modBatch, delSongIds, delPaths);

                        dirTimestamps.clear();
            dirTimestamps.putAll(newDirTimestamps);
            syncAllToRedis();
            flushSnapshot();

            long elapsed = System.currentTimeMillis() - t0;
            log.info("全量对账完成: {}ms, 磁盘{}文件, DB{}记录, 新增{} 修改{} 删除{}",
                    elapsed, diskPaths.size(), dbMap.size(), newFiles, modifiedFiles, goneFiles);
        } catch (Exception e) {
            log.error("全量对账异常", e);
        } finally {
            fullScanning.set(false);
        }
    }


    private void loadSnapshot() {
        int count = 0;

        if (redisEnabled) {
            try {
                var entries = redis.opsForHash().entries(REDIS_KEY);
                if (!entries.isEmpty()) {
                    for (var entry : entries.entrySet()) {
                        try {
                            dirTimestamps.put((String) entry.getKey(),
                                    Long.parseLong((String) entry.getValue()));
                            count++;
                        } catch (NumberFormatException ignored) {}
                    }
                    log.info("快照已从 Redis 加载: {} 条目录记录", count);
                    return;
                }
                log.info("Redis 快照为空，尝试从 DB 加载");
            } catch (Exception e) {
                log.warn("从 Redis 加载快照失败: {}，回退到 DB", e.getMessage());
            }
        }

        try {
            List<FileWatchSnapshot> rows = snapshotMapper.selectAll();
            for (FileWatchSnapshot row : rows) {
                if (row.getDirPath() != null) {
                    dirTimestamps.put(row.getDirPath(), row.getLastModified());
                    count++;
                }
            }
            log.info("快照已从 DB 加载: {} 条目录记录", count);

            if (count > 0 && redisEnabled) {
                syncAllToRedis();
            }
        } catch (Exception e) {
            log.warn("加载快照失败（表可能尚不存在），将执行全量扫描: {}", e.getMessage());
        }
    }

    private void syncAllToRedis() {
        if (!redisEnabled || dirTimestamps.isEmpty()) return;
        try {
            redis.delete(REDIS_KEY);
            Map<String, String> batch = new HashMap<>(dirTimestamps.size());
            dirTimestamps.forEach((k, v) -> batch.put(k, String.valueOf(v)));
            redis.opsForHash().putAll(REDIS_KEY, batch);
            log.debug("快照已全量同步至 Redis: {} 条", batch.size());
        } catch (Exception e) {
            log.warn("同步快照至 Redis 失败: {}", e.getMessage());
        }
    }

    private void syncDirToRedis(String dirPath, long timestamp) {
        if (!redisEnabled) return;
        try {
            redis.opsForHash().put(REDIS_KEY, dirPath, String.valueOf(timestamp));
        } catch (Exception e) {
            log.debug("Redis HSET 失败（忽略）: {}", e.getMessage());
        }
    }

    private void flushSnapshot() {
        try {
            if (dirTimestamps.isEmpty()) return;

            List<FileWatchSnapshot> batch = new ArrayList<>(5000);
            for (var entry : dirTimestamps.entrySet()) {
                FileWatchSnapshot snap = new FileWatchSnapshot();
                snap.setDirPath(entry.getKey());
                snap.setLastModified(entry.getValue());
                batch.add(snap);

                if (batch.size() >= 5000) {
                    snapshotMapper.batchUpsert(batch);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                snapshotMapper.batchUpsert(batch);
            }
            log.debug("快照已刷入 DB: {} 条", dirTimestamps.size());
        } catch (Exception e) {
            log.error("刷入快照失败", e);
        }
    }


    private Map<String, long[]> queryDbPathsUnderDir(String dirAbs) {
        String prefix = dirAbs.endsWith(File.separator) ? dirAbs : dirAbs + File.separator;
        try {
            List<Map<String, Object>> rows = musicMapper.selectSongPathsUnderRoot(prefix);
            if (rows == null || rows.isEmpty()) return Map.of();
            Map<String, long[]> result = new HashMap<>();
            for (Map<String, Object> row : rows) {
                String path = String.valueOf(row.get("FILE_PATH"));
                if (!path.startsWith(prefix)) continue;
                long id = numFromRow(row, "ID");
                long mtime = numFromRow(row, "FILE_MTIME");
                result.put(path, new long[]{id, mtime});
            }
            return result;
        } catch (Exception e) {
            log.warn("查询 DB 路径失败: {}", e.getMessage());
            return Map.of();
        }
    }

    private Map<String, long[]> queryAllDbPaths() {
        try {
            String rootPrefix = rootDir.toString();
            if (!rootPrefix.endsWith(File.separator)) {
                rootPrefix = rootPrefix + File.separator;
            }
            List<Map<String, Object>> rows = musicMapper.selectSongPathsUnderRoot(rootPrefix);
            if (rows == null || rows.isEmpty()) return Map.of();
            Map<String, long[]> result = new HashMap<>();
            for (Map<String, Object> row : rows) {
                String path = String.valueOf(row.get("FILE_PATH"));
                long id = numFromRow(row, "ID");
                long mtime = numFromRow(row, "FILE_MTIME");
                result.put(path, new long[]{id, mtime});
            }
            return result;
        } catch (Exception e) {
            log.warn("全量查询 DB 路径失败: {}", e.getMessage());
            return Map.of();
        }
    }

    private static long numFromRow(Map<String, Object> row, String col) {
        Object v = row.get(col);
        return v instanceof Number n ? n.longValue() : 0L;
    }


    @Override
    public boolean isRunning() { return running; }
    public int snapshotSize() { return dirTimestamps.size(); }
    public boolean isScanning() { return incrementalScanning.get() || fullScanning.get(); }
}
