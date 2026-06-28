package com.gjl.music.controller.song;

import com.gjl.music.dto.DedupDeleteRequest;
import com.gjl.music.model.*;
import com.gjl.music.module.song.enrich.module.EnrichPipeline;
import com.gjl.music.infra.pipeline.*;
import com.gjl.music.infra.pipeline.graph.GraphNode;
import com.gjl.music.infra.pipeline.graph.PipelineGraph;
import com.gjl.music.service.song.PipelineService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 管道 REST API —— 管道生命周期管理 + 调试端点。
 */
@Slf4j
@RestController
@RequestMapping("/api/pipelines")
@PreAuthorize("hasAuthority('pipeline:write')")
public class PipelineController {

    private final PipelineService pipelineService;
    private final EnrichPipeline enrichPipeline;

    public PipelineController(PipelineService pipelineService,
                              EnrichPipeline enrichPipeline) {
        this.pipelineService = pipelineService;
        this.enrichPipeline = enrichPipeline;
    }

    /** 列出所有管道 */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listPipelines() {
        return ResponseEntity.ok(pipelineService.listPipelines());
    }

    /** 查询管道状态 */
    @GetMapping("/{pipelineId}")
    public ResponseEntity<Map<String, Object>> getPipeline(@PathVariable String pipelineId) {
        PipelineState state = pipelineService.getPipelineState(pipelineId);
        if (state == null) {
            return ResponseEntity.notFound().build();
        }
        PipelineProgress progress = pipelineService.getPipelineProgress(pipelineId);
        PipelineTaskInfo info = pipelineService.getPipelineInfo(pipelineId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pipelineId", progress.getPipelineId());
        result.put("templateName", info != null ? info.getTemplateName() : "");
        result.put("state", progress.getState().name());
        result.put("totalFiles", progress.getTotalFiles());
        result.put("successFiles", progress.getSuccessFiles());
        result.put("failedFiles", progress.getFailedFiles());
        result.put("errorModule", progress.getErrorNode() != null ? progress.getErrorNode() : "");
        result.put("errorMessage", progress.getErrorMessage() != null ? progress.getErrorMessage() : "");
        result.put("inputPaths", info != null && info.getInputPaths() != null ? info.getInputPaths() : "");
        result.put("createdAt", progress.getCreatedAt() != null ? progress.getCreatedAt() : "");
        result.put("durationMs", progress.getDurationMs());
        return ResponseEntity.ok(result);
    }

    /** 查询去重分组 */
    @GetMapping("/{pipelineId}/dedup-groups")
    public ResponseEntity<?> getDedupGroups(
            @PathVariable String pipelineId,
            @RequestParam(required = false) String strategy) {
        PipelineState state = pipelineService.getPipelineState(pipelineId);
        if (state == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(pipelineService.getDedupGroups(pipelineId, strategy));
    }

    /** 执行去重删除 —— 支持单文件删除和规则批量删除 */
    @PostMapping("/{pipelineId}/dedup-delete")
    public ResponseEntity<?> executeDedupDelete(
            @PathVariable String pipelineId,
            @RequestBody DedupDeleteRequest request) {
        PipelineState state = pipelineService.getPipelineState(pipelineId);
        if (state == null) {
            return ResponseEntity.notFound().build();
        }
        try {
            Map<String, Object> result = pipelineService.executeDedupDelete(pipelineId, request);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("去重删除失败 [{}]", pipelineId, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "去重删除失败: " + e.getMessage()));
        }
    }

    /** 分页查询管道处理项日志 */
    @GetMapping("/{pipelineId}/items")
    public ResponseEntity<Map<String, Object>> getItems(
            @PathVariable String pipelineId,
            @RequestParam(defaultValue = "SUCCESS") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        PipelineState state = pipelineService.getPipelineState(pipelineId);
        if (state == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(pipelineService.getPipelineItems(pipelineId, status, page, size));
    }

    @PostMapping("/{pipelineId}/pause")
    public ResponseEntity<Map<String, Object>> pause(@PathVariable String pipelineId) {
        boolean ok = pipelineService.pausePipeline(pipelineId);
        if (!ok) return ResponseEntity.badRequest().body(Map.of("error", "无法暂停"));
        return ResponseEntity.ok(Map.of("pipelineId", pipelineId, "state", "PAUSED"));
    }

    @PostMapping("/{pipelineId}/resume")
    public ResponseEntity<Map<String, Object>> resume(@PathVariable String pipelineId) {
        boolean ok = pipelineService.resumePipeline(pipelineId);
        if (!ok) return ResponseEntity.badRequest().body(Map.of("error", "无法恢复"));
        return ResponseEntity.ok(Map.of("pipelineId", pipelineId, "state", "RUNNING"));
    }

    @PostMapping("/{pipelineId}/cancel")
    public ResponseEntity<Map<String, Object>> cancel(@PathVariable String pipelineId) {
        boolean ok = pipelineService.cancelPipeline(pipelineId);
        if (!ok) return ResponseEntity.badRequest().body(Map.of("error", "无法取消"));
        return ResponseEntity.ok(Map.of("pipelineId", pipelineId, "state", "CANCELLED"));
    }

    /** 列出可用模块 */
    @GetMapping("/modules")
    public ResponseEntity<Map<String, Object>> listModules() {
        return ResponseEntity.ok(Map.of("modules", pipelineService.moduleNames()));
    }

    /** 列出可用模板 */
    @GetMapping("/templates")
    public ResponseEntity<Map<String, Object>> listTemplates() {
        return ResponseEntity.ok(Map.of("templates", pipelineService.templateNames()));
    }

    /** 音乐 API 增强搜索 */
    @GetMapping("/enrich-search")
    public ResponseEntity<Map<String, Object>> testEnrichSearch(
            @RequestParam(defaultValue = "qqmusic") String provider,
            @RequestParam(defaultValue = "") String title,
            @RequestParam(defaultValue = "") String artist,
            @RequestParam(required = false) String album,
            @RequestParam(required = false) String year,
            @RequestParam(required = false) String trackNumber,
            @RequestParam(required = false) String discNumber,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) String company,
            @RequestParam(required = false) String albumYear) {
        if (title.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "title 不能为空"));

        MusicMetadata meta = buildMeta(title, artist, album, year, trackNumber, discNumber,
                language, genre, company, albumYear);
        List<MusicMetadata> results = enrichPipeline.search(provider, meta);
        return ResponseEntity.ok(Map.of(
                "provider", provider,
                "query", artist + " " + title,
                "results", (Object) results
        ));
    }

    /** 音乐 API 调试 —— 搜索并返回结果 */
    @GetMapping("/enrich-debug")
    public ResponseEntity<Map<String, Object>> debugEnrich(
            @RequestParam(defaultValue = "qqmusic") String provider,
            @RequestParam(defaultValue = "晴天") String title,
            @RequestParam(defaultValue = "周杰伦") String artist) {

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("provider", provider);
        result.put("query", artist + " " + title);

        List<MusicMetadata> results = enrichPipeline.search(provider, buildMeta(title, artist,
                null, null, null, null, null, null, null, null));
        result.put("results", results);
        return ResponseEntity.ok(result);
    }

    // ── Provider 列表 ──

    @GetMapping("/enrich-providers")
    public List<Map<String, String>> listProviders() {
        return enrichPipeline.getProviders();
    }

    /** 查询单首歌曲完整详情 */
    @GetMapping("/enrich-detail")
    public ResponseEntity<Map<String, Object>> enrichDetail(
            @RequestParam String provider,
            @RequestParam String songId) {
        Map<String, Object> result = enrichPipeline.getSongDetail(provider, songId);
        log.info("enrich detail: {}", result);
        return ResponseEntity.ok(result);
    }

    // ── DAG 管道端点 ──

    /** 提交 DAG 管道 */
    @PostMapping("/dag")
    public ResponseEntity<Map<String, Object>> submitDagPipeline(@RequestBody DagPipelineRequest request) {
        if (request.getPaths() == null || request.getPaths().length == 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "paths 不能为空"));
        }
        if (request.getNodes() == null || request.getNodes().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "nodes 不能为空"));
        }

        PipelineGraph graph;
        try {
            graph = parseGraph(request.getNodes());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }

        String pipelineId;
        try {
            pipelineId = pipelineService.submitDagPipeline(
                    graph, request.getPaths(), request.getOptions());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }

        log.info("DagPipeline [{}] submitted with {} nodes", pipelineId, graph.size());
        return ResponseEntity.ok(Map.of(
                "pipelineId", pipelineId,
                "state", "RUNNING",
                "nodes", graph.size()
        ));
    }

    private PipelineGraph parseGraph(List<Map<String, Object>> nodeList) {
        List<GraphNode> nodes = new ArrayList<>();
        for (Map<String, Object> raw : nodeList) {
            String id = (String) raw.get("id");
            String moduleName = (String) raw.get("moduleName");

            GraphNode.Builder builder = GraphNode.builder(id)
                    .moduleName(moduleName != null ? moduleName : id);

            @SuppressWarnings("unchecked")
            List<String> deps = (List<String>) raw.getOrDefault("dependsOn", List.of());
            builder.dependsOn(deps.toArray(new String[0]));

            @SuppressWarnings("unchecked")
            Map<String, Object> config = (Map<String, Object>) raw.getOrDefault("config", Map.of());
            builder.configAll(config);

            // 最后一个节点默认为 finalNode
            Boolean isFinal = (Boolean) raw.get("finalNode");
            builder.finalNode(isFinal != null ? isFinal : false);

            nodes.add(builder.build());
        }

        // 确保至少有一个 finalNode
        if (nodes.stream().noneMatch(GraphNode::isFinalNode) && !nodes.isEmpty()) {
            // 设置最后一个节点为 finalNode
            GraphNode last = nodes.get(nodes.size() - 1);
            nodes.set(nodes.size() - 1, GraphNode.builder(last.getId())
                    .handler(last.getHandler())
                    .dependsOn(last.getDependencies().toArray(new String[0]))
                    .moduleName(last.getModuleName())
                    .configAll(last.getConfig())
                    .finalNode(true)
                    .build());
        }

        return new PipelineGraph(nodes);
    }

    // ── DTO ──

    public static class DagPipelineRequest {
        private String[] paths;
        private List<Map<String, Object>> nodes;
        private Map<String, Object> options;

        public String[] getPaths() { return paths; }
        public void setPaths(String[] paths) { this.paths = paths; }
        public List<Map<String, Object>> getNodes() { return nodes; }
        public void setNodes(List<Map<String, Object>> nodes) { this.nodes = nodes; }
        public Map<String, Object> getOptions() { return options; }
        public void setOptions(Map<String, Object> options) { this.options = options; }
    }

    // ── 工具方法 ──

    private static MusicMetadata buildMeta(String title, String artist, String album,
            String year, String track, String disc, String lang, String genre,
            String company, String albumYear) {
        MusicMetadata m = new MusicMetadata();
        m.addSong(Song.builder().title(title).year(year)
                .trackNumber(intOrNull(track)).discNumber(intOrNull(disc)).language(lang).build());
        if (artist != null && !artist.isBlank())
            m.addArtist(Artist.builder().artistName(artist).build());
        if (album != null && !album.isBlank())
            m.addAlbum(Album.builder().albumName(album).company(company)
                    .albumYear(intOrNull(albumYear)).build());
        if (genre != null && !genre.isBlank())
            m.addStyle(Style.builder().styleName(genre).build());
        return m;
    }

    private static Integer intOrNull(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
    }
}
