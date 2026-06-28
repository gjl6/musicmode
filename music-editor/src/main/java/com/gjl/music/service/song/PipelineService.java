package com.gjl.music.service.song;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gjl.music.dto.DedupDeleteRequest;
import com.gjl.music.mapper.SongMapper;
import com.gjl.music.editor.mapper.SongManageMapper;
import com.gjl.music.editor.mapper.PipelineDedupGroupMapper;
import com.gjl.music.model.PipelineDedupGroup;
import com.gjl.music.infra.pipeline.*;
import com.gjl.music.infra.pipeline.engine.PipelineEngine;
import com.gjl.music.infra.pipeline.graph.PipelineGraph;
import com.gjl.music.infra.util.PathUtils;
import com.gjl.music.search.EntityChangeEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 管道生命周期服务 —— 管道的提交、查询、控制。
 *
 * <p>不包含业务逻辑，纯委托给 PipelineFactory 和 PipelineOrchestrator。</p>
 */
@Slf4j
@Service
public class PipelineService {

    private static final int BATCH_SIZE = 200;

    private final PipelineFactory pipelineFactory;
    private final PipelineOrchestrator orchestrator;
    private final PipelinePersistence persistence;
    private final PipelineDedupGroupMapper dedupGroupMapper;
    private final SongMapper songMapper;
    private final SongManageMapper songManageMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PipelineService(PipelineFactory pipelineFactory, PipelineOrchestrator orchestrator,
                           PipelinePersistence persistence,
                           PipelineDedupGroupMapper dedupGroupMapper,
                           SongMapper songMapper,
                           SongManageMapper songManageMapper,
                           ApplicationEventPublisher eventPublisher) {
        this.pipelineFactory = pipelineFactory;
        this.orchestrator = orchestrator;
        this.persistence = persistence;
        this.dedupGroupMapper = dedupGroupMapper;
        this.songMapper = songMapper;
        this.songManageMapper = songManageMapper;
        this.eventPublisher = eventPublisher;
    }

    /** 异步提交 DAG 管道，options 通过 context slot 传递给模块的 configure() */
    public String submitDagPipeline(PipelineGraph graph, String[] paths,
                                     Map<String, Object> options) {
        String pipelineId = UUID.randomUUID().toString().replace("-", "");

        java.nio.file.Path[] resolvedPaths = new java.nio.file.Path[paths.length];
        for (int i = 0; i < paths.length; i++) {
            resolvedPaths[i] = pipelineFactory.resolvePath(paths[i]);
        }

        PipelineEngine engine = pipelineFactory.createFromGraph(graph, pipelineId,
                ctx -> {
                    ctx.setSlot("input.paths", resolvedPaths);
                    if (options != null && !options.isEmpty()) {
                        ctx.setSlot("options", options);
                    }
                },
                orchestrator::pushItemLog);
        String graphJson = pipelineFactory.getGraphJson(pipelineId);

        return orchestrator.submit(engine, null, graphJson, paths);
    }

    /** 从模板提交管道 */
    public String submitTemplate(String templateName, String path,
                                  Map<String, Object> options) {
        java.nio.file.Path resolved = pipelineFactory.resolvePath(path);
        String pipelineId = UUID.randomUUID().toString().replace("-", "");
        PipelineEngine engine = pipelineFactory.createFromTemplate(templateName, pipelineId,
                ctx -> {
                    ctx.setSlot("input.paths", new java.nio.file.Path[]{ resolved });
                    if (options != null) {
                        ctx.setSlot("options", options);
                    }
                },
                orchestrator::pushItemLog);
        String graphJson = pipelineFactory.getGraphJson(pipelineId);

        return orchestrator.submit(engine, templateName, graphJson,
                new String[]{ path });
    }

    /**
     * 从模板提交数据驱动的管道（不依赖文件路径）。
     * slots 中的键值对将被设置到 NodeContext 的初始 slot 中。
     */
    public String submitDataTemplate(String templateName,
                                     Map<String, Object> slots) {
        String pipelineId = UUID.randomUUID().toString().replace("-", "");
        PipelineEngine engine = pipelineFactory.createFromTemplate(templateName, pipelineId,
                ctx -> {
                    if (slots != null) {
                        slots.forEach(ctx::setSlot);
                    }
                },
                orchestrator::pushItemLog);
        String graphJson = pipelineFactory.getGraphJson(pipelineId);

        return orchestrator.submit(engine, templateName, graphJson,
                new String[0]);
    }

    public PipelineState getPipelineState(String pipelineId) {
        return orchestrator.getState(pipelineId);
    }

    public PipelineProgress getPipelineProgress(String pipelineId) {
        return orchestrator.getProgress(pipelineId);
    }

    public PipelineTaskInfo getPipelineInfo(String pipelineId) {
        return orchestrator.getTaskInfo(pipelineId);
    }

    public List<Map<String, Object>> listPipelines() {
        return orchestrator.listAll();
    }

    public boolean pausePipeline(String id) { return orchestrator.pause(id); }
    public boolean resumePipeline(String id) { return orchestrator.resume(id); }
    public boolean cancelPipeline(String id) { return orchestrator.cancel(id); }

    public Set<String> moduleNames() { return pipelineFactory.moduleNames(); }
    public Set<String> templateNames() { return pipelineFactory.templateNames(); }

    /** 分页查询管道 item log */
    public Map<String, Object> getPipelineItems(String pipelineId, String status, int page, int size) {
        return orchestrator.getPipelineItems(pipelineId, status, page, size);
    }

    /** 查询去重分组（按策略过滤） */
    public List<PipelineDedupGroup> getDedupGroups(String pipelineId, String strategy) {
        if (strategy != null && !strategy.isEmpty() && !"all".equals(strategy)) {
            return dedupGroupMapper.selectByPipelineIdAndStrategy(pipelineId, strategy);
        }
        return dedupGroupMapper.selectByPipelineId(pipelineId);
    }

    /**
     * 执行去重删除 —— 同步操作，支持单文件删除和规则批量删除。
     *
     * <p>关键：删除文件后遍历该管道 <b>所有策略</b> 的去重分组，
     * 全部清理掉已删除的文件路径，保证跨策略数据一致。</p>
     *
     * @return 包含 deletedFiles、deletedRecords、deletedPaths 的 Map
     */
    public Map<String, Object> executeDedupDelete(String pipelineId, DedupDeleteRequest request) {
        PipelineState state = orchestrator.getState(pipelineId);
        if (state == null) {
            throw new IllegalArgumentException("管道不存在: " + pipelineId);
        }

        // ═══ 步骤1：确定要删除的文件集合 ═══
        Set<String> pathsToDelete = new LinkedHashSet<>();

        if ("single".equals(request.getAction())) {
            String fp = request.getFilePath();
            if (fp == null || fp.isBlank()) {
                throw new IllegalArgumentException("单文件删除需提供 filePath");
            }
            pathsToDelete.add(fp);
        } else if ("rule".equals(request.getAction())) {
            String strategy = request.getStrategy();
            if (strategy == null || strategy.isBlank()) {
                throw new IllegalArgumentException("规则删除需提供 strategy");
            }
            List<PipelineDedupGroup> groups =
                    dedupGroupMapper.selectByPipelineIdAndStrategy(pipelineId, strategy);
            pathsToDelete = applyDeleteRule(groups, request.getRule(), request.getRuleOptions());
        } else {
            throw new IllegalArgumentException("未知 action: " + request.getAction()
                    + "，支持 single / rule");
        }

        if (pathsToDelete.isEmpty()) {
            return Map.of("deletedFiles", 0, "deletedRecords", 0,
                    "deletedPaths", List.of(), "message", "没有需要删除的文件");
        }

        log.info("去重删除 [{}]: action={}, 待删文件数={}", pipelineId,
                request.getAction(), pathsToDelete.size());

        // ═══ 步骤2：删除磁盘文件 + 查询 DB songId ═══
        int deletedFiles = 0;
        List<String> normalizedPaths = new ArrayList<>();
        Map<String, Long> pathToSongId = new HashMap<>();

        for (String rawPath : pathsToDelete) {
            Path p = Path.of(rawPath);
            String absPath = p.toAbsolutePath().normalize().toString();
            normalizedPaths.add(absPath);

            // 查询 DB 中的 songId
            Long songId = songMapper.selectSongIdByFilePath(absPath);
            if (songId != null) {
                pathToSongId.put(absPath, songId);
            }

            // 删除磁盘文件
            try {
                if (Files.deleteIfExists(p)) {
                    deletedFiles++;
                    log.debug("已删除文件: {}", absPath);
                }
            } catch (IOException e) {
                log.warn("删除文件失败: {} — {}", absPath, e.getMessage());
            }
        }

        // ═══ 步骤3：批量清理 DB 记录 ═══
        int deletedRecords = 0;
        List<Long> songIdsToDelete = new ArrayList<>(pathToSongId.values());
        if (!songIdsToDelete.isEmpty()) {
            for (int i = 0; i < songIdsToDelete.size(); i += BATCH_SIZE) {
                int end = Math.min(i + BATCH_SIZE, songIdsToDelete.size());
                List<Long> batch = songIdsToDelete.subList(i, end);
                songManageMapper.deleteSongArtistsBySongIds(batch);
                songManageMapper.deleteSongStylesBySongIds(batch);
                songManageMapper.deleteLyricsBySongIds(batch);
                songManageMapper.deleteSongsByIds(batch);
                deletedRecords += batch.size();
            }
            log.info("去重删除: 已清理 {} 条 DB 记录", deletedRecords);

            // ★ 发布索引删除事件
            for (Long songId : songIdsToDelete) {
                eventPublisher.publishEvent(EntityChangeEvent.songDeleted(songId));
            }
            log.debug("已发布 {} 个去重删除事件", songIdsToDelete.size());
        }

        // ═══ 步骤4：更新所有策略的去重分组 ═══
        List<PipelineDedupGroup> allGroups =
                dedupGroupMapper.selectByPipelineId(pipelineId);

        for (PipelineDedupGroup group : allGroups) {
            List<String> filePaths = parseJsonArray(group.getFilePaths());
            List<String> fileNames = parseJsonArray(group.getFileNames());

            // 收集要移除的索引
            List<Integer> removeIdx = new ArrayList<>();
            for (int i = 0; i < filePaths.size(); i++) {
                String groupPath = normalizePath(filePaths.get(i));
                if (pathsToDelete.stream().anyMatch(d ->
                        normalizePath(d).equals(groupPath))) {
                    removeIdx.add(i);
                }
            }

            if (removeIdx.isEmpty()) continue;

            // 从后往前移除
            for (int i = removeIdx.size() - 1; i >= 0; i--) {
                int idx = removeIdx.get(i);
                filePaths.remove(idx);
                fileNames.remove(idx);
            }

            // 更新 DB
            try {
                group.setFilePaths(objectMapper.writeValueAsString(filePaths));
                group.setFileNames(objectMapper.writeValueAsString(fileNames));
            } catch (JsonProcessingException e) {
                log.warn("序列化去重分组失败: {}", e.getMessage());
                continue;
            }
            dedupGroupMapper.update(group);
        }

        // ═══ 步骤5：清理空分组和只有 1 个文件的分组 ═══
        int cleanedGroups = 0;
        // 重新加载更新后的分组，找出文件数 ≤ 1 的
        List<PipelineDedupGroup> updatedGroups =
                dedupGroupMapper.selectByPipelineId(pipelineId);
        for (PipelineDedupGroup g : updatedGroups) {
            List<String> remaining = parseJsonArray(g.getFilePaths());
            if (remaining.size() <= 1 && g.getId() != null) {
                dedupGroupMapper.deleteById(g.getId());
                cleanedGroups++;
            }
        }
        if (cleanedGroups > 0) {
            log.info("去重删除: 清理了 {} 个无效分组（文件数 ≤ 1）", cleanedGroups);
        }

        log.info("去重删除完成 [{}]: 删除 {} 个文件, {} 条 DB 记录, 影响 {} 个分组",
                pipelineId, deletedFiles, deletedRecords, allGroups.size());

        return Map.of(
                "deletedFiles", deletedFiles,
                "deletedRecords", deletedRecords,
                "deletedPaths", new ArrayList<>(pathsToDelete)
        );
    }

    // ── 规则算法 ──

    /**
     * 对指定策略的去重分组应用删除规则，返回待删除文件路径集合。
     */
    @SuppressWarnings("unchecked")
    private Set<String> applyDeleteRule(List<PipelineDedupGroup> groups,
                                         String rule, Map<String, Object> ruleOptions) {
        Set<String> toDelete = new LinkedHashSet<>();

        String directory = null;
        List<String> preferredFormats = List.of("flac", "wav", "ape", "aiff", "m4a", "mp3", "ogg", "wma");

        if (ruleOptions != null) {
            if (ruleOptions.get("directory") instanceof String dir && !dir.isBlank()) {
                directory = PathUtils.normalize(dir);
            }
            if (ruleOptions.get("preferredFormats") instanceof List<?> fmts) {
                preferredFormats = fmts.stream()
                        .map(f -> String.valueOf(f).toLowerCase().replace(".", ""))
                        .collect(Collectors.toList());
            }
        }

        for (PipelineDedupGroup group : groups) {
            List<String> filePaths = parseJsonArray(group.getFilePaths());
            if (filePaths.size() <= 1) continue;

            int keepIdx = 0; // 默认保留第一个

            switch (rule != null ? rule : "keepFirst") {
                case "keepFirst":
                    keepIdx = 0;
                    break;

                case "keepByDirectory":
                    // 查找第一个在指定目录下的文件
                    keepIdx = 0;
                    if (directory != null) {
                        for (int i = 0; i < filePaths.size(); i++) {
                            String normalized = PathUtils.normalize(filePaths.get(i));
                            if (normalized.contains(directory)) {
                                keepIdx = i;
                                break;
                            }
                        }
                    }
                    break;

                case "keepPreferredFormat":
                    // 按格式优先级选择保留的文件
                    int bestRank = Integer.MAX_VALUE;
                    keepIdx = 0;
                    for (int i = 0; i < filePaths.size(); i++) {
                        String ext = getExtension(filePaths.get(i));
                        int rank = preferredFormats.indexOf(ext);
                        if (rank == -1) rank = Integer.MAX_VALUE - 1;
                        if (rank < bestRank) {
                            bestRank = rank;
                            keepIdx = i;
                        }
                    }
                    break;

                default:
                    keepIdx = 0;
                    break;
            }

            // 除了 keepIdx 之外的加入待删除
            for (int i = 0; i < filePaths.size(); i++) {
                if (i != keepIdx) {
                    toDelete.add(filePaths.get(i));
                }
            }
        }

        return toDelete;
    }

    // ── 辅助方法 ──

    private List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank() || "[]".equals(json.trim())) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("解析 JSON 数组失败: {}", json, e);
            return new ArrayList<>();
        }
    }

    private static String normalizePath(String path) {
        if (path == null) return "";
        return Path.of(path).toAbsolutePath().normalize().toString();
    }

    private static String getExtension(String path) {
        if (path == null) return "";
        int dot = path.lastIndexOf('.');
        if (dot < 0 || dot == path.length() - 1) return "";
        return path.substring(dot + 1).toLowerCase();
    }
}
