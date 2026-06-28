package com.gjl.music.module.song.dedup.strategy;

import com.gjl.music.editor.mapper.SongManageMapper;
import com.gjl.music.infra.util.PathUtils;
import com.gjl.music.model.MusicMetadata;
import com.gjl.music.module.song.dedup.DuplicateGroup;
import com.gjl.music.infra.pipeline.NodeContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 基于音乐元数据的重复检测（title + artist + duration + album）。
 * DB 优先缓存；缺失则 ctx.fork(METADATA_PARSE) 按需解析 + 批量回写 DB；
 * 全部补完后 SQL 查重。
 */
@Slf4j
@Component
public class MetadataDedupStrategy implements DedupStrategy {

    private static final int BATCH_SIZE = 200;
    private static final int PERSIST_BATCH = 50;

    private final SongManageMapper songManageMapper;

    public MetadataDedupStrategy(SongManageMapper songManageMapper) {
        this.songManageMapper = songManageMapper;
    }

    @Override public String name() { return "metadata"; }
    @Override public String label() { return "元数据"; }

    @Override
    @SuppressWarnings("unchecked")
    public List<DuplicateGroup> detect(List<Path> filePaths, Map<String, Object> options, NodeContext ctx) {
        if (filePaths.isEmpty()) return List.of();

        // 提取根目录（供最后 SQL 查重用）
        Set<String> rootDirs = new LinkedHashSet<>();
        for (Path p : filePaths) {
            Path parent = p.getParent();
            if (parent != null) rootDirs.add(PathUtils.escapeMySqlLike(parent.toString()));
        }
        List<String> rootDirList = new ArrayList<>(rootDirs);

        // 分批：查缓存 → 补缺 → 回写
        for (int offset = 0; offset < filePaths.size(); offset += BATCH_SIZE) {
            int end = Math.min(offset + BATCH_SIZE, filePaths.size());
            List<Path> batch = filePaths.subList(offset, end);
            List<String> batchPaths = batch.stream().map(Path::toString).toList();

            // 1. 批量查 DB 缓存
            Map<String, Map<String, Object>> cached = new LinkedHashMap<>();
            for (Map<String, Object> row : songManageMapper.findMetadataByPaths(batchPaths)) {
                String p = (String) row.get("FILEPATH");
                if (p != null) cached.put(p, row);
            }

            // 2. 收集缺数据的文件（整条记录不存在 或 标题为空）
            Map<String, Path> needsParse = new LinkedHashMap<>();
            for (Path p : batch) {
                String filePath = p.toString();
                Map<String, Object> row = cached.get(filePath);
                boolean missing = (row == null)
                        || isBlank(String.valueOf(row.getOrDefault("TITLE", "")));
                if (missing) needsParse.put(filePath, p);
            }

            // 3. 批量 invoke parser（GapFillingModule 内部并行处理）
            Map<String, MusicMetadata> toPersist = new LinkedHashMap<>();
            if (!needsParse.isEmpty()) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> parserResult = ctx.invoke("parser", needsParse);
                    // parser 返回格式: {"node.parser.output": Map<Path, MusicMetadata>}
                    for (Object v : parserResult.values()) {
                        if (v instanceof Map<?, ?> innerMap) {
                            for (var inner : innerMap.entrySet()) {
                                if (inner.getValue() instanceof MusicMetadata meta
                                        && !meta.getImmutableSongs().isEmpty()
                                        && !isBlank(meta.getImmutableSongs().getFirst().getTitle())) {
                                    toPersist.put(inner.getKey().toString(), meta);
                                }
                            }
                        }
                    }
                    log.debug("元数据去重: 批量解析 {} 个文件, 成功 {} 个", needsParse.size(), toPersist.size());
                } catch (Exception e) {
                    log.warn("按需解析元数据失败: {} 个文件 - {}", needsParse.size(), e.getMessage());
                }
            }

            // 4. 批量回写 DB（parser 只解析不持久化，需手动入库）
            if (!toPersist.isEmpty()) {
                int subOffset = 0;
                List<String> keys = new ArrayList<>(toPersist.keySet());
                while (subOffset < keys.size()) {
                    int subEnd = Math.min(subOffset + PERSIST_BATCH, keys.size());
                    Map<String, MusicMetadata> sub = new LinkedHashMap<>();
                    for (String k : keys.subList(subOffset, subEnd)) {
                        sub.put(k, toPersist.get(k));
                    }
                    try {
                        ctx.invoke("db-operator", sub);
                    } catch (Exception e) {
                        log.error("批量回写 DB 失败: {}", e.getMessage());
                    }
                    subOffset = subEnd;
                }
                log.debug("元数据去重: 回写 {} 条到 DB", toPersist.size());
            }
        }

        // 5. SQL 查重
        String mode = options != null ? String.valueOf(options.getOrDefault("metadataMode", "standard")) : "standard";
        List<Map<String, Object>> rows = songManageMapper.findMetadataForDedup(rootDirList);
        if (rows == null || rows.isEmpty()) {
            log.info("元数据去重: DB 中无数据");
            return List.of();
        }

        Map<String, List<MetaEntry>> groups = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String title = normalize(String.valueOf(row.getOrDefault("TITLE", "")));
            String artistNames = normalizeArtists(String.valueOf(row.getOrDefault("ARTISTNAMES", "")));
            String albumName = normalize(String.valueOf(row.getOrDefault("ALBUMNAME", "")));
            Object durObj = row.get("DURATION");
            int duration = durObj instanceof Number ? ((Number) durObj).intValue() : 0;

            if (title.isBlank()) continue;
            if (artistNames.isBlank() && "strict".equals(mode)) continue;

            String key = buildKey(mode, title, artistNames, duration, albumName);
            groups.computeIfAbsent(key, k -> new ArrayList<>())
                    .add(new MetaEntry(
                            String.valueOf(row.getOrDefault("FILEPATH", "")),
                            String.valueOf(row.getOrDefault("FILENAME", ""))));
        }

        List<DuplicateGroup> results = new ArrayList<>();
        for (Map.Entry<String, List<MetaEntry>> e : groups.entrySet()) {
            if (e.getValue().size() > 1) {
                List<String> paths = e.getValue().stream().map(m -> m.filePath).toList();
                List<String> names = e.getValue().stream().map(m -> m.fileName).toList();
                results.add(new DuplicateGroup(DuplicateGroup.DuplicateType.METADATA, paths, names));
            }
        }

        log.info("元数据去重 (mode={}): {} 个重复组 ({} 首歌)", mode, results.size(), rows.size());
        return results;
    }

    private String buildKey(String mode, String title, String artist, int duration, String album) {
        return switch (mode) {
            case "loose" -> title + "|" + artist;
            case "strict" -> title + "|" + artist + "|" + bucketDuration(duration) + "|" + album;
            default -> title + "|" + artist + "|" + bucketDuration(duration);
        };
    }

    private int bucketDuration(int seconds) {
        return Math.round(seconds / 5f) * 5;
    }

    static String normalize(String s) {
        if (s == null) return "";
        return s.toLowerCase()
                .replaceAll("\\([^)]*\\)", "")
                .replaceAll("\\[.*?\\]", "")
                .replaceAll("\\p{Punct}", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    static String normalizeArtists(String s) {
        if (s == null || s.isBlank()) return "";
        String[] parts = s.split("[,;&]|\\s+feat\\.?\\s+|\\s+ft\\.?\\s+|\\s+featuring\\s+");
        return Arrays.stream(parts)
                .map(MetadataDedupStrategy::normalize)
                .filter(p -> !p.isBlank())
                .sorted()
                .collect(Collectors.joining(","));
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }

    private record MetaEntry(String filePath, String fileName) {}
}
