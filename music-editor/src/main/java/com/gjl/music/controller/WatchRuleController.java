package com.gjl.music.controller;

import com.gjl.music.infra.pipeline.NodeHandler;
import com.gjl.music.infra.pipeline.PipelineFactory;
import com.gjl.music.model.WatchRule;
import com.gjl.music.module.Module;
import com.gjl.music.service.WatchRuleService;
import com.gjl.music.watch.AutoTaskRunner;
import com.gjl.music.watch.AutoTaskScheduler;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自动任务 REST API — CRUD + 启停 + 手动触发。
 */
@RestController
@RequestMapping("/api/watch")
@PreAuthorize("hasAuthority('config:write')")
public class WatchRuleController {

    private final WatchRuleService ruleService;
    private final AutoTaskScheduler scheduler;
    private final AutoTaskRunner runner;
    private final PipelineFactory pipelineFactory;

    public WatchRuleController(WatchRuleService ruleService,
                               AutoTaskScheduler scheduler,
                               AutoTaskRunner runner,
                               PipelineFactory pipelineFactory) {
        this.ruleService = ruleService;
        this.scheduler = scheduler;
        this.runner = runner;
        this.pipelineFactory = pipelineFactory;
    }

    // ═══════════════════════════════════════════════════
    // CRUD
    // ═══════════════════════════════════════════════════

    @GetMapping("/profiles")
    public List<WatchRule> list() {
        return ruleService.listAll();
    }

    @GetMapping("/profiles/{id}")
    public WatchRule get(@PathVariable Long id) {
        return ruleService.getById(id);
    }

    @PostMapping("/profiles")
    public WatchRule create(@RequestBody WatchRule rule) {
        return ruleService.create(rule);
    }

    @PutMapping("/profiles/{id}")
    public WatchRule update(@PathVariable Long id, @RequestBody WatchRule rule) {
        rule.setId(id);
        return ruleService.update(rule);
    }

    @DeleteMapping("/profiles/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        ruleService.delete(id);
        return Map.of("success", true);
    }

    // ═══════════════════════════════════════════════════
    // 启停控制
    // ═══════════════════════════════════════════════════

    @PostMapping("/profiles/{id}/pause")
    public Map<String, Object> pause(@PathVariable Long id) {
        scheduler.pauseTask(id);
        return Map.of("success", true);
    }

    @PostMapping("/profiles/{id}/resume")
    public Map<String, Object> resume(@PathVariable Long id) {
        scheduler.resumeTask(id);
        return Map.of("success", true);
    }

    @GetMapping("/profiles/{id}/state")
    public Map<String, Object> state(@PathVariable Long id) {
        WatchRule task = ruleService.getById(id);
        if (task == null) return Map.of("error", "not found");
        return Map.of(
                "state", task.getState() != null ? task.getState() : "IDLE",
                "lastScanAt", task.getLastScanAt() != null ? task.getLastScanAt().toString() : null,
                "lastRunAt", task.getLastRunAt() != null ? task.getLastRunAt().toString() : null,
                "paused", scheduler.isPaused(id)
        );
    }

    // ═══════════════════════════════════════════════════
    // 手动触发
    // ═══════════════════════════════════════════════════

    /**
     * 对指定文件列表手动执行规则。
     * Body: { "paths": ["path1.mp3", "path2.mp3"] }
     */
    @PostMapping("/profiles/{id}/execute")
    public Map<String, Object> execute(@PathVariable Long id,
                                        @RequestBody Map<String, List<String>> body) {
        List<String> paths = body.getOrDefault("paths", List.of());
        if (paths.isEmpty()) {
            return Map.of("success", false, "error", "paths is required");
        }
        List<Path> filePaths = paths.stream().map(Path::of).toList();
        String pipelineId = ruleService.submitBatch(id, filePaths);
        return Map.of("success", true, "pipelineId", pipelineId != null ? pipelineId : "");
    }

    /**
     * 立即触发一次扫描（DIR_SCAN + FILE_SCAN 都执行）。
     */
    @PostMapping("/profiles/{id}/scan")
    public Map<String, Object> scanNow(@PathVariable Long id) {
        WatchRule task = ruleService.getById(id);
        if (task == null) return Map.of("success", false, "error", "task not found");
        if (!task.isEnabled()) return Map.of("success", false, "error", "task disabled");

        try {
            if (task.isDirScanEnabled()) {
                ConcurrentHashMap<String, Long> timestamps = scheduler.getDirTimestamps(id);
                runner.runDirScan(task, timestamps);
            }
            if (task.isFileScanEnabled()) {
                runner.runFileScan(task);
            }
            // 管道提交由 WatchRuleService.onFileChangesDetected 监听事件处理
            return Map.of("success", true);
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════
    // 元数据
    // ═══════════════════════════════════════════════════

    /**
     * 列出所有可用的处理步骤（从 PipelineFactory 动态获取，Module 自描述元数据）。
     * 仅返回 {@link Module#isUserVisible()} 为 true 的模块。
     * 每个步骤包含 configSchema（配置表单字段）和 selectionSchema（数据来源选择，仅 artist/album）。
     */
    @GetMapping("/steps")
    public List<Map<String, Object>> steps() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (String moduleName : pipelineFactory.moduleNames()) {
            NodeHandler handler = pipelineFactory.getHandler(moduleName);
            if (!(handler instanceof Module m) || !m.isUserVisible()) continue;

            Map<String, Object> info = new LinkedHashMap<>();
            info.put("name", m.name());
            info.put("label", m.label());
            info.put("category", inferCategory(m.name()));
            info.put("configSchema", m.configSchema().isEmpty() ? null : m.configSchema());
            info.put("selectionSchema", getSelectionSchema(inferCategory(m.name())));
            result.add(info);
        }
        // 按 category 排序：song → artist → album
        result.sort((a, b) -> {
            String catA = (String) a.get("category");
            String catB = (String) b.get("category");
            int orderA = switch (catA) { case "song" -> 0; case "artist" -> 1; case "album" -> 2; default -> 9; };
            int orderB = switch (catB) { case "song" -> 0; case "artist" -> 1; case "album" -> 2; default -> 9; };
            int cmp = Integer.compare(orderA, orderB);
            if (cmp != 0) return cmp;
            return ((String) a.get("name")).compareTo((String) b.get("name"));
        });
        return result;
    }

    /** 按模块名前缀推断分类 */
    private static String inferCategory(String moduleName) {
        if (moduleName == null || moduleName.isBlank()) return "song";
        if (moduleName.startsWith("artist-")) return "artist";
        if (moduleName.startsWith("album-")) return "album";
        return "song";
    }

    /**
     * 数据来源选择 schema — 仅 artist/album 类别需要。
     * 描述 scanner 的 selection 参数，前端据此渲染数据来源配置表单。
     */
    private static Map<String, Object> getSelectionSchema(String category) {
        if ("song".equals(category)) return null;
        return switch (category) {
            case "artist" -> Map.of(
                "label", "数据来源",
                "description", "选择要处理的艺术家范围",
                "fields", List.of(
                    Map.of("key", "mode", "label", "选择模式", "type", "select",
                        "default", "all",
                        "options", List.of(
                            Map.of("value", "all", "label", "全部艺术家"),
                            Map.of("value", "incomplete", "label", "信息不完整"),
                            Map.of("value", "unenriched", "label", "未增强"),
                            Map.of("value", "nonstandard", "label", "名称不规范"),
                            Map.of("value", "naked", "label", "裸数据（仅名称）"),
                            Map.of("value", "duplicates", "label", "疑似重复"),
                            Map.of("value", "letter", "label", "按首字母"),
                            Map.of("value", "keyword", "label", "按关键词搜索"),
                            Map.of("value", "ids", "label", "指定 ID 列表")
                        )),
                    Map.of("key", "letter", "label", "首字母", "type", "string",
                        "visibleWhen", Map.of("mode", "letter")),
                    Map.of("key", "keyword", "label", "关键词", "type", "string",
                        "visibleWhen", Map.of("mode", "keyword")),
                    Map.of("key", "style", "label", "流派筛选", "type", "string",
                        "placeholder", "可选，精确匹配流派名称"),
                    Map.of("key", "country", "label", "国家筛选", "type", "string",
                        "placeholder", "可选，精确匹配国家名称"),
                    Map.of("key", "minSongs", "label", "最少歌曲数", "type", "number",
                        "default", 0),
                    Map.of("key", "maxSongs", "label", "最多歌曲数", "type", "number",
                        "default", 0)
                ));
            case "album" -> Map.of(
                "label", "数据来源",
                "description", "选择要处理的专辑范围",
                "fields", List.of(
                    Map.of("key", "mode", "label", "选择模式", "type", "select",
                        "default", "all",
                        "options", List.of(
                            Map.of("value", "all", "label", "全部专辑"),
                            Map.of("value", "incomplete", "label", "信息不完整"),
                            Map.of("value", "unenriched", "label", "未增强"),
                            Map.of("value", "naked", "label", "裸数据（仅名称）"),
                            Map.of("value", "duplicates", "label", "疑似重复"),
                            Map.of("value", "letter", "label", "按首字母"),
                            Map.of("value", "keyword", "label", "按关键词搜索"),
                            Map.of("value", "ids", "label", "指定 ID 列表")
                        )),
                    Map.of("key", "letter", "label", "首字母", "type", "string",
                        "visibleWhen", Map.of("mode", "letter")),
                    Map.of("key", "keyword", "label", "关键词", "type", "string",
                        "visibleWhen", Map.of("mode", "keyword")),
                    Map.of("key", "artistId", "label", "艺术家 ID", "type", "number",
                        "placeholder", "可选，筛选特定艺术家的专辑"),
                    Map.of("key", "minSongs", "label", "最少歌曲数", "type", "number",
                        "default", 0),
                    Map.of("key", "maxSongs", "label", "最多歌曲数", "type", "number",
                        "default", 0)
                ));
            default -> null;
        };
    }
}
