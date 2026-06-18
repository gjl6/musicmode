package com.gjl.music.service;

import com.gjl.music.filesystem.BrowseResult;
import com.gjl.music.filesystem.FileInfo;
import com.gjl.music.model.MusicMetadata;
import com.gjl.music.module.song.filesystem.FileSystemModule;
import com.gjl.music.parser.ParserFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;


@Slf4j
@Service
public class BrowseService {

    private final FileSystemModule fileSystem;
    private final ParserFactory parserFactory;
    private final String musicRootDir;
    private final Executor executor;

    public BrowseService(FileSystemModule fileSystem,
                         ParserFactory parserFactory,
                         @Value("${music.root-dir}") String musicRootDir,
                         @Qualifier("foregroundExecutor") Executor executor) {
        this.fileSystem = fileSystem;
        this.parserFactory = parserFactory;
        this.musicRootDir = musicRootDir;
        this.executor = executor;
    }


    public Map<String, Object> listDirectory(String rawPath) {
        String path = decode(rawPath);
        File root = new File(musicRootDir);
        BrowseResult browse = fileSystem.browse(root, path);

        List<Map<String, Object>> files = new ArrayList<>();
        for (FileInfo fi : browse.files()) {
            files.add(basicFileInfo(fi));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("path", browse.path());
        response.put("folders", browse.folders());
        response.put("files", files);
        return response;
    }


    public MusicMetadata getMetadata(String rawPath) {
        String path = decode(rawPath);
        path = normalizePath(path);
        File root = new File(musicRootDir);
        File file = fileSystem.resolveFile(root, path);

        if (!file.exists() || !file.isFile()) {
            throw new IllegalArgumentException("文件不存在: " + path);
        }

        MusicMetadata meta = parserFactory.parse(file);
        if (meta == null) {
            throw new IllegalArgumentException("解析失败: " + path);
        }
        return meta;
    }


    public Map<String, Object> getDirectoryMetadata(String rawPath, int page, int pageSize) {
        String path = decode(rawPath);
        File root = new File(musicRootDir);
        BrowseResult browse = fileSystem.browse(root, path);

        List<FileInfo> fileList = browse.files();
        if (fileList.isEmpty()) {
            return buildResponse(browse, List.of(), 0, page, pageSize);
        }

                List<FileInfo> sorted = new ArrayList<>(fileList);
        sorted.sort(Comparator.comparing(FileInfo::name));

        int total = sorted.size();
        int from = Math.min((page - 1) * pageSize, total);
        int to = Math.min(from + pageSize, total);

                List<FileInfo> pageSlice = sorted.subList(from, to);
        List<CompletableFuture<Map<String, Object>>> futures = pageSlice.stream()
            .map(fi -> CompletableFuture.supplyAsync(() -> {
                Map<String, Object> info = basicFileInfo(fi);
                try {
                    File file = fileSystem.resolveFile(root, fi.path());
                    MusicMetadata meta = parserFactory.parse(file);
                    info.put("meta", meta);
                } catch (Exception e) {
                    info.put("meta", null);
                }
                return info;
            }, executor))
            .toList();

        List<Map<String, Object>> files = futures.stream()
            .map(CompletableFuture::join)
            .toList();

        return buildResponse(browse, files, total, page, pageSize);
    }


    private static Map<String, Object> basicFileInfo(FileInfo fi) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("id", uuidFromPath(fi.path()));
        info.put("name", fi.name());
        info.put("fileName", fi.fileName());
        info.put("format", fi.format());
        info.put("path", fi.path());
        info.put("size", fi.size());
        info.put("modifiedTime", fi.modifiedTime());
        return info;
    }

    private Map<String, Object> buildResponse(BrowseResult browse, List<Map<String, Object>> files,
                                              int total, int page, int pageSize) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("path", browse.path());
        response.put("folders", browse.folders());
        response.put("files", files);
        response.put("total", total);
        response.put("page", page);
        response.put("pageSize", pageSize);
        return response;
    }

    private static String uuidFromPath(String path) {
        return UUID.nameUUIDFromBytes(path.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static String decode(String raw) {
        return URLDecoder.decode(raw != null ? raw : "", StandardCharsets.UTF_8);
    }


    private String normalizePath(String path) {
        if (path == null || path.isEmpty()) return path;
        try {
            Path p = Paths.get(path);
            if (p.isAbsolute()) {
                Path root = Paths.get(musicRootDir).toAbsolutePath().normalize();
                Path normalized = p.normalize();
                if (normalized.startsWith(root)) {
                    return root.relativize(normalized).toString();
                }
            }
        } catch (Exception ignored) {}
        return path;
    }
}
