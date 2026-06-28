package com.gjl.music.module.song.scanner;

import com.gjl.music.exception.PipelineException;
import com.gjl.music.module.FailurePolicy;
import com.gjl.music.infra.pipeline.NodeContext;
import com.gjl.music.infra.pipeline.NodeHandler;
import com.gjl.music.infra.pipeline.NodeResult;
import com.gjl.music.scanner.ScannerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class ScannerModuleImpl implements ScannerModule, NodeHandler {

    private final ScannerService scannerService;

    public ScannerModuleImpl(ScannerService scannerService) {
        this.scannerService = scannerService;
    }

    @Override public String name() { return "scanner"; }
    @Override public FailurePolicy failurePolicy() { return FailurePolicy.FAIL_FAST; }
    @Override public boolean isUserVisible() { return false; }

    // ── 领域方法 — 委托给 common ScannerService ──

    @Override
    public List<Path> scan(Path... roots) {
        return scannerService.scan(roots);
    }

    @Override
    public boolean isAudioFile(Path path) {
        return scannerService.isAudioFile(path);
    }

    // ── 流式模式：数据源 ──

    public void produce(BlockingQueue<Object> outputQueue, NodeContext context) {
        Path[] roots = resolvePaths(context.getSlot("input.paths"));
        if (roots == null || roots.length == 0) {
            throw new PipelineException("未指定扫描路径");
        }
        log.info("开始流式扫描，根路径数: {}", roots.length);
        int count = 0;
        for (Path root : roots) {
            if (!Files.exists(root)) {
                throw new PipelineException("路径不存在: " + root);
            }
            try {
                count += produceScanItems(root, outputQueue);
            } catch (IOException e) {
                throw new PipelineException("扫描失败: " + root, e);
            }
        }
        log.info("流式扫描完成，发现 {} 个音频文件", count);
    }

    private int produceScanItems(Path root, BlockingQueue<Object> outputQueue) throws IOException {
        if (!Files.isDirectory(root)) {
            if (scannerService.isAudioFile(root)) {
                offerToQueue(outputQueue, root.toAbsolutePath());
                return 1;
            }
            return 0;
        }
        AtomicInteger count = new AtomicInteger();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (scannerService.isAudioFile(file)) {
                    offerToQueue(outputQueue, file.toAbsolutePath());
                    count.incrementAndGet();
                }
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                log.warn("无法访问: {} - {}", file, exc.getMessage());
                return FileVisitResult.CONTINUE;
            }
        });
        return count.get();
    }

    private static void offerToQueue(BlockingQueue<Object> queue, Path path) {
        try {
            queue.put(path);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ── 批量模式 ──

    @Override
    public NodeResult execute(NodeContext ctx) throws Exception {
        NodeResult result = new NodeResult();
        // 拓扑模式：从 input.paths 读取；invoke 模式：从 input 读取（GapFillingModule 等基类委托扫描）
        Path[] roots = resolvePaths(ctx.getSlot("input.paths"));
        if (roots == null || roots.length == 0) {
            roots = resolvePaths(ctx.getSlot("input"));
        }
        if (roots == null || roots.length == 0) {
            throw new PipelineException("未指定扫描路径，请设置 input.paths");
        }

        log.info("开始扫描，根路径数: {}", roots.length);
        List<Path> audioFiles = scannerService.scan(roots);

        List<Path> scanResult = Collections.unmodifiableList(audioFiles);
        ctx.setSlot("node.scanner.output", scanResult);
        result.addOutput("node.scanner.output", scanResult);
        log.info("扫描完成，发现 {} 个音频文件", scanResult.size());
        return result;
    }

    private Path[] resolvePaths(Object raw) {
        if (raw instanceof Path[] paths) return paths;
        if (raw instanceof List<?> list) return list.stream()
                .filter(Path.class::isInstance).map(Path.class::cast).toArray(Path[]::new);
        if (raw instanceof String[] strs) return Arrays.stream(strs)
                .map(Path::of).toArray(Path[]::new);
        if (raw instanceof String str) return new Path[]{Path.of(str)};
        return null;
    }
}
