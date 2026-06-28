package com.gjl.music.module.song.dedup.strategy;

import com.gjl.music.infra.util.FileHashUtils;
import com.gjl.music.model.MusicMetadata;
import com.gjl.music.model.Song;
import com.gjl.music.module.song.dedup.DuplicateGroup;
import com.gjl.music.infra.pipeline.NodeContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Path;
import java.util.*;

/**
 * 基于 SHA-256 文件内容哈希的重复检测。
 * DB 优先缓存；缺失则磁盘计算并回写 DB。
 */
@Slf4j
@Component
public class HashDedupStrategy implements DedupStrategy {

    private static final int BATCH_SIZE = 500;

    @Override public String name() { return "hash"; }
    @Override public String label() { return "文件哈希"; }

    @Override
    public List<DuplicateGroup> detect(List<Path> filePaths, Map<String, Object> options, NodeContext ctx) {
        Map<String, List<FileInfo>> hashGroups = new LinkedHashMap<>();

        int total = filePaths.size();
        for (int offset = 0; offset < total; offset += BATCH_SIZE) {
            int end = Math.min(offset + BATCH_SIZE, total);
            List<Path> batch = filePaths.subList(offset, end);
            Map<String, MusicMetadata> toPersist = new LinkedHashMap<>();

            for (Path p : batch) {
                String filePath = p.toString();
                String hash = null;

                File f = p.toFile();
                if (f.exists()) {
                    hash = FileHashUtils.getFileHash(f);
                    if (hash != null && !hash.isBlank()) {
                        MusicMetadata meta = new MusicMetadata();
                        meta.addSong(Song.builder().filePath(filePath).fileHash(hash).build());
                        toPersist.put(filePath, meta);
                    }
                }

                if (hash != null && !hash.isBlank()) {
                    hashGroups.computeIfAbsent(hash, k -> new ArrayList<>())
                            .add(new FileInfo(filePath, p.getFileName().toString()));
                }
            }

            if (!toPersist.isEmpty()) {
                try {
                    ctx.invoke("db-operator", toPersist);
                } catch (Exception e) {
                    log.warn("Hash 去重: 回填 DB 失败: {}", e.getMessage());
                }
                log.debug("Hash 去重: 回填 {} 条 file_hash 到 DB", toPersist.size());
            }
        }

        List<DuplicateGroup> results = new ArrayList<>();
        for (Map.Entry<String, List<FileInfo>> e : hashGroups.entrySet()) {
            if (e.getValue().size() > 1) {
                List<String> paths = e.getValue().stream().map(FileInfo::path).toList();
                List<String> names = e.getValue().stream().map(FileInfo::name).toList();
                results.add(new DuplicateGroup(DuplicateGroup.DuplicateType.FILE_HASH, paths, names));
            }
        }

        log.info("Hash 去重: {} 个文件, {} 个重复组", total, results.size());
        return results;
    }

    private record FileInfo(String path, String name) {}
}
