package com.gjl.music.playback.infra.transcoding;

import com.gjl.music.playback.config.PlaybackProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * 基于文件的 LRU 转码缓存。
 *
 * <h3>存储结构</h3>
 * <pre>{@code
 *   {cacheDir}/
 *     00/
 *       01/a1b2c3...d4.mp3    ← 缓存文件
 *       02/...
 *     01/
 *       ...
 * }</pre>
 *
 * <p>两级子目录分散存储，避免单目录文件数过多。
 * 驱逐策略：按最后访问时间排序，最旧优先删除。</p>
 */
@Slf4j
@Service
public class FileBasedTranscodingCache implements TranscodingCache {

    private final Path cacheDir;
    private final long maxSizeBytes;
    private final ConcurrentMap<String, Path> index = new ConcurrentHashMap<>();
    private final AtomicLong currentSize = new AtomicLong(0);

    public FileBasedTranscodingCache(PlaybackProperties properties) {
        PlaybackProperties.Cache cache = properties.getCache();
        this.cacheDir = Path.of(cache.getTranscodingCacheDir());
        this.maxSizeBytes = cache.getTranscodingCacheMaxSizeMB() * 1024L * 1024L;
    }

    @PostConstruct
    void init() throws IOException {
        Files.createDirectories(cacheDir);
        // 扫描已有缓存文件，重建索引和大小统计
        try (Stream<Path> files = Files.walk(cacheDir)) {
            files.filter(Files::isRegularFile).forEach(f -> {
                String name = f.getFileName().toString();
                index.put(name, f);
                try {
                    currentSize.addAndGet(Files.size(f));
                } catch (IOException ignored) {}
            });
        }
        log.info("转码缓存初始化: dir={}, 已有文件={}, 大小={}MB",
                cacheDir, index.size(), currentSize.get() / (1024 * 1024));
    }

    @Override
    public Path getOrCompute(String cacheKey, Supplier<InputStream> transcoder) {
        // 缓存命中
        Path existing = index.get(cacheKey);
        if (existing != null && Files.exists(existing)) {
            try {
                // 更新访问时间（用于 LRU）
                Files.setLastModifiedTime(existing, java.nio.file.attribute.FileTime.fromMillis(
                        System.currentTimeMillis()));
            } catch (IOException ignored) {}
            return existing;
        }

        // 缓存未命中：转码并写入文件
        Path dir = cacheDir.resolve(cacheKey.substring(0, Math.min(2, cacheKey.length())));
        Path outFile = dir.resolve(cacheKey);

        try {
            Files.createDirectories(dir);
            try (InputStream in = transcoder.get();
                 OutputStream os = Files.newOutputStream(outFile)) {
                long written = in.transferTo(os);
                currentSize.addAndGet(written);
                index.put(cacheKey, outFile);
                log.debug("转码缓存写入: key={}, size={}KB", cacheKey, written / 1024);
            }

            // 检查是否超出容量
            if (currentSize.get() > maxSizeBytes) {
                evictToTarget(maxSizeBytes * 9 / 10); // 驱逐至 90%
            }

            return outFile;
        } catch (Exception e) {
            // 清理失败文件
            try { Files.deleteIfExists(outFile); } catch (IOException ignored) {}
            log.warn("转码缓存写入失败: key={}, error={}", cacheKey, e.getMessage());
            throw new RuntimeException("转码缓存写入失败", e);
        }
    }

    @Override
    public long getCurrentSize() {
        return currentSize.get();
    }

    @Override
    public int getFileCount() {
        return index.size();
    }

    @Override
    public void evictToTarget(long targetBytes) {
        if (currentSize.get() <= targetBytes) return;

        // 按最后修改时间排序（最旧优先）
        try (Stream<Path> files = Files.walk(cacheDir)) {
            files.filter(Files::isRegularFile)
                    .sorted(Comparator.comparingLong(f -> {
                        try {
                            return Files.readAttributes(f, BasicFileAttributes.class)
                                    .lastModifiedTime().toMillis();
                        } catch (IOException e) { return 0L; }
                    }))
                    .forEach(f -> {
                        if (currentSize.get() <= targetBytes) return;
                        try {
                            long size = Files.size(f);
                            Files.deleteIfExists(f);
                            currentSize.addAndGet(-size);
                            index.remove(f.getFileName().toString());
                        } catch (IOException ignored) {}
                    });
        } catch (IOException e) {
            log.warn("缓存驱逐扫描失败: {}", e.getMessage());
        }

        log.debug("缓存驱逐完成: 当前大小={}MB", currentSize.get() / (1024 * 1024));
    }
}
