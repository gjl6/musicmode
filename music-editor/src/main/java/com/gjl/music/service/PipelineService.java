package com.gjl.music.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gjl.music.dto.DedupDeleteRequest;
import com.gjl.music.mapper.MusicMapper;
import com.gjl.music.mapper.PipelineDedupGroupMapper;
import com.gjl.music.model.PipelineDedupGroup;
import com.gjl.music.pipeline.*;
import com.gjl.music.pipeline.engine.PipelineEngine;
import com.gjl.music.pipeline.graph.PipelineGraph;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;


@Slf4j
@Service
public class PipelineService {

    private static final int BATCH_SIZE = 200;

    private final PipelineFactory pipelineFactory;
    private final PipelineOrchestrator orchestrator;
    private final PipelinePersistence persistence;
    private final PipelineDedupGroupMapper dedupGroupMapper;
    private final MusicMapper musicMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PipelineService(PipelineFactory pipelineFactory, PipelineOrchestrator orchestrator,
                           PipelinePersistence persistence,
                           PipelineDedupGroupMapper dedupGroupMapper,
                           MusicMapper musicMapper) {
        this.pipelineFactory = pipelineFactory;
        this.orchestrator = orchestrator;
        this.persistence = persistence;
        this.dedupGroupMapper = dedupGroupMapper;
        this.musicMapper = musicMapper;
    }


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


    public Map<String, Object> getPipelineItems(String pipelineId, String status, int page, int size) {
        return orchestrator.getPipelineItems(pipelineId, status, page, size);
    }


    public List<PipelineDedupGroup> getDedupGroups(String pipelineId, String strategy) {
        if (strategy != null && !strategy.isEmpty() && !"all".equals(strategy)) {
            return dedupGroupMapper.selectByPipelineIdAndStrategy(pipelineId, strategy);
        }
        return dedupGroupMapper.selectByPipelineId(pipelineId);
    }


    public Map<String, Object> executeDedupDelete(String pipelineId, DedupDeleteRequest request) {
        PipelineState state = orchestrator.getState(pipelineId);
        if (state == null) {
            throw new IllegalArgumentException("管道不存在: " + pipelineId);
        }

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

                int deletedFiles = 0;
        List<String> normalizedPaths = new ArrayList<>();
        Map<String, Long> pathToSongId = new HashMap<>();

        for (String rawPath : pathsToDelete) {
            Path p = Path.of(rawPath);
            String absPath = p.toAbsolutePath().normalize().toString();
            normalizedPaths.add(absPath);

                        Long songId = musicMapper.selectSongIdByFilePath(absPath);
            if (songId != null) {
                pathToSongId.put(absPath, songId);
            }

                        try {
                if (Files.deleteIfExists(p)) {
                    deletedFiles++;
                    log.debug("已删除文件: {}", absPath);
                }
            } catch (IOException e) {
                log.warn("删除文件失败: {} — {}", absPath, e.getMessage());
            }
        }

                int deletedRecords = 0;
        List<Long> songIdsToDelete = new ArrayList<>(pathToSongId.values());
        if (!songIdsToDelete.isEmpty()) {
            for (int i = 0; i < songIdsToDelete.size(); i += BATCH_SIZE) {
                int end = Math.min(i + BATCH_SIZE, songIdsToDelete.size());
                List<Long> batch = songIdsToDelete.subList(i, end);
                musicMapper.deleteSongArtistsBySongIds(batch);
                musicMapper.deleteSongStylesBySongIds(batch);
                musicMapper.deleteLyricsBySongIds(batch);
                musicMapper.deleteSongsByIds(batch);
                deletedRecords += batch.size();
            }
            log.info("去重删除: 已清理 {} 条 DB 记录", deletedRecords);
        }

                List<PipelineDedupGroup> allGroups =
                dedupGroupMapper.selectByPipelineId(pipelineId);

        for (PipelineDedupGroup group : allGroups) {
            List<String> filePaths = parseJsonArray(group.getFilePaths());
            List<String> fileNames = parseJsonArray(group.getFileNames());

                        List<Integer> removeIdx = new ArrayList<>();
            for (int i = 0; i < filePaths.size(); i++) {
                String groupPath = normalizePath(filePaths.get(i));
                if (pathsToDelete.stream().anyMatch(d ->
                        normalizePath(d).equals(groupPath))) {
                    removeIdx.add(i);
                }
            }

            if (removeIdx.isEmpty()) continue;

                        for (int i = removeIdx.size() - 1; i >= 0; i--) {
                int idx = removeIdx.get(i);
                filePaths.remove(idx);
                fileNames.remove(idx);
            }

                        try {
                group.setFilePaths(objectMapper.writeValueAsString(filePaths));
                group.setFileNames(objectMapper.writeValueAsString(fileNames));
            } catch (JsonProcessingException e) {
                log.warn("序列化去重分组失败: {}", e.getMessage());
                continue;
            }
            dedupGroupMapper.update(group);
        }

                int cleanedGroups = 0;
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


    @SuppressWarnings("unchecked")
    private Set<String> applyDeleteRule(List<PipelineDedupGroup> groups,
                                         String rule, Map<String, Object> ruleOptions) {
        Set<String> toDelete = new LinkedHashSet<>();

        String directory = null;
        List<String> preferredFormats = List.of("flac", "wav", "ape", "aiff", "m4a", "mp3", "ogg", "wma");

        if (ruleOptions != null) {
            if (ruleOptions.get("directory") instanceof String dir && !dir.isBlank()) {
                directory = dir.replace('\\', '/');
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

            int keepIdx = 0;

            switch (rule != null ? rule : "keepFirst") {
                case "keepFirst":
                    keepIdx = 0;
                    break;

                case "keepByDirectory":
                                        keepIdx = 0;
                    if (directory != null) {
                        for (int i = 0; i < filePaths.size(); i++) {
                            String normalized = filePaths.get(i).replace('\\', '/');
                            if (normalized.contains(directory)) {
                                keepIdx = i;
                                break;
                            }
                        }
                    }
                    break;

                case "keepPreferredFormat":
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

                        for (int i = 0; i < filePaths.size(); i++) {
                if (i != keepIdx) {
                    toDelete.add(filePaths.get(i));
                }
            }
        }

        return toDelete;
    }


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
