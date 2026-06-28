package com.gjl.music.module.song.filesystem;

import com.gjl.music.exception.PipelineException;
import com.gjl.music.filesystem.BrowseResult;
import com.gjl.music.filesystem.FileInfo;
import com.gjl.music.filesystem.FileSystemService;
import com.gjl.music.infra.util.PathUtils;
import com.gjl.music.module.FailurePolicy;
import com.gjl.music.infra.pipeline.NodeContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.BlockingQueue;

@Slf4j
@Component
public class FileSystemModuleImpl implements FileSystemModule {

    private final FileSystemService fileSystemService;

    public FileSystemModuleImpl(FileSystemService fileSystemService) {
        this.fileSystemService = fileSystemService;
    }

    @Override public String name() { return "filesystem"; }
    @Override public FailurePolicy failurePolicy() { return FailurePolicy.FAIL_FAST; }
    @Override public boolean isUserVisible() { return false; }

    // ── 领域方法 — 委托给 common FileSystemService ──

    @Override
    public BrowseResult browse(File root, String relativePath) {
        return fileSystemService.browse(root, relativePath);
    }

    @Override
    public File resolveFile(File root, String relativePath) {
        return fileSystemService.resolveFile(root, relativePath);
    }

    @Override
    public void validatePath(File root, File target) {
        fileSystemService.validatePath(root, target);
    }

    // ── 流式数据源 ──

    public void produce(BlockingQueue<Object> outputQueue,
                        NodeContext context) {
        String rootPath = context.getSlot("root.dir");
        String requestPath = context.getSlot("request.path");
        Boolean singleFile = context.getSlot("single.file");

        File root = new File(rootPath);

        // 外部指定路径列表（来自 options.files）→ 文件直接输出，目录浏览当前层级
        Path[] inputPaths = context.getSlot("input.paths");
        if (inputPaths != null && inputPaths.length > 0) {
            List<Path> allFiles = new ArrayList<>();
            for (Path p : inputPaths) {
                if (Files.isDirectory(p)) {
                    String rel = PathUtils.normalize(root.toPath().relativize(p).toString());
                    BrowseResult br = fileSystemService.browse(root, rel);
                    for (FileInfo fi : br.files()) {
                        Path fp = new File(root, fi.path()).toPath();
                        allFiles.add(fp);
                        try { outputQueue.put(fp); }
                        catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                    }
                } else {
                    allFiles.add(p);
                    try { outputQueue.put(p); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                }
            }
            context.setSlot("node.scanner.output", allFiles);
            return;
        }

        String relativePath = (requestPath != null) ? requestPath : "";

        if (Boolean.TRUE.equals(singleFile)) {
            File file = fileSystemService.resolveFile(root, relativePath);
            if (!file.exists() || !file.isFile()) {
                throw new PipelineException("文件不存在: " + relativePath);
            }
            List<Path> paths = List.of(file.toPath());
            context.setSlot("node.scanner.output", paths);
            try { outputQueue.put(file.toPath()); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        } else {
            BrowseResult browse = fileSystemService.browse(root, relativePath);
            context.setSlot("browse.result", browse);

            List<Path> paths = new ArrayList<>();
            for (FileInfo fi : browse.files()) {
                Path p = new File(root, fi.path()).toPath();
                paths.add(p);
                try { outputQueue.put(p); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
            }
            context.setSlot("node.scanner.output", paths);
        }
    }
}
