package com.gjl.music.module.song.dedup.strategy;

import com.gjl.music.module.song.dedup.DuplicateGroup;
import com.gjl.music.infra.pipeline.NodeContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.*;

/**
 * 基于文件名的重复检测。纯内存操作，不依赖 DB。
 */
@Slf4j
@Component
public class FileNameDedupStrategy implements DedupStrategy {

    @Override public String name() { return "filename"; }
    @Override public String label() { return "文件名"; }

    @Override
    public List<DuplicateGroup> detect(List<Path> filePaths, Map<String, Object> options, NodeContext ctx) {
        Map<String, List<Path>> groups = new LinkedHashMap<>();
        for (Path p : filePaths) {
            String name = p.getFileName().toString().toLowerCase();
            groups.computeIfAbsent(name, k -> new ArrayList<>()).add(p);
        }

        List<DuplicateGroup> results = new ArrayList<>();
        for (Map.Entry<String, List<Path>> e : groups.entrySet()) {
            if (e.getValue().size() > 1) {
                List<String> paths = e.getValue().stream().map(Path::toString).toList();
                List<String> names = e.getValue().stream().map(p -> p.getFileName().toString()).toList();
                results.add(new DuplicateGroup(DuplicateGroup.DuplicateType.FILE_NAME, paths, names));
            }
        }
        log.info("文件名去重: {} 个重复组 ({} 个文件)", results.size(), filePaths.size());
        return results;
    }
}
