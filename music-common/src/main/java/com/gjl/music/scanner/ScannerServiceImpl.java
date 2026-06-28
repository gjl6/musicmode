package com.gjl.music.scanner;

import com.gjl.music.config.ConfigChangedEvent;
import com.gjl.music.config.ConfigService;
import com.gjl.music.exception.ModuleException;
import com.gjl.music.exception.PipelineException;
import com.gjl.music.infra.util.AudioFileUtils;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 文件扫描服务实现 —— 从 editor 的 ScannerModuleImpl 提取核心业务逻辑。
 */
@Slf4j
@Component
public class ScannerServiceImpl implements ScannerService {

    private final ConfigService configService;

    /** 热更新：流式扫描阈值 */
    private volatile int streamingThreshold = 2000;

    public ScannerServiceImpl(ConfigService configService) {
        this.configService = configService;
    }

    @PostConstruct
    void reloadConfig() {
        if (configService != null) {
            this.streamingThreshold = configService.getInt("scanner.streaming_threshold", 2000);
        }
    }

    @EventListener
    public void onConfigChanged(ConfigChangedEvent e) {
        if ("scanner.streaming_threshold".equals(e.configKey())) {
            this.streamingThreshold = e.asInt(2000);
        }
    }

    // ── 领域方法 ──

    @Override
    public List<Path> scan(Path... roots) {
        if (roots.length == 0) {
            throw new PipelineException("至少需要指定一个扫描路径");
        }
        List<Path> audioFiles = new ArrayList<>();
        for (Path root : roots) {
            if (!Files.exists(root)) {
                throw new PipelineException("路径不存在: " + root);
            }
            try {
                if (Files.isDirectory(root)) {
                    scanWalkTree(root, audioFiles);
                } else if (AudioFileUtils.isAudioFile(root)) {
                    audioFiles.add(root.toAbsolutePath());
                }
            } catch (IOException e) {
                throw new ModuleException("scanner", root.toString(), "扫描失败: " + root, e);
            }
        }
        return Collections.unmodifiableList(audioFiles);
    }

    @Override
    public boolean isAudioFile(Path path) {
        return AudioFileUtils.isAudioFile(path);
    }

    @Override
    public int estimateFileCount(Path dir) throws IOException {
        AtomicInteger count = new AtomicInteger();
        Files.walkFileTree(dir, Collections.emptySet(), 3, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                count.incrementAndGet();
                if (count.get() > streamingThreshold) return FileVisitResult.TERMINATE;
                return FileVisitResult.CONTINUE;
            }
        });
        return count.get();
    }

    // ── 内部扫描实现 ──

    void scanWalkTree(Path dir, List<Path> sink) throws IOException {
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (AudioFileUtils.isAudioFile(file)) {
                    sink.add(file.toAbsolutePath());
                }
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                log.warn("无法访问: {} - {}", file, exc.getMessage());
                return FileVisitResult.CONTINUE;
            }
        });
    }

    void scanStreaming(Path dir, List<Path> sink) throws IOException {
        log.info("大目录启用流式扫描: {}", dir);
        try (var stream = Files.walk(dir, FileVisitOption.FOLLOW_LINKS)) {
            stream.filter(AudioFileUtils::isAudioFile)
                    .map(Path::toAbsolutePath)
                    .forEach(sink::add);
        } catch (UncheckedIOException e) {
            throw new ModuleException("scanner", dir.toString(), "流式扫描失败: " + dir, e.getCause());
        }
    }

    /** 根据文件数量阈值选择扫描策略 */
    void scanWithStrategy(Path root, List<Path> sink) throws IOException {
        if (!Files.isDirectory(root)) {
            if (AudioFileUtils.isAudioFile(root)) sink.add(root.toAbsolutePath());
            return;
        }
        if (estimateFileCount(root) > streamingThreshold) {
            scanStreaming(root, sink);
        } else {
            scanWalkTree(root, sink);
        }
    }

    int getStreamingThreshold() {
        return streamingThreshold;
    }
}
