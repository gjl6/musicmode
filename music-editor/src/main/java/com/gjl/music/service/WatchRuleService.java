package com.gjl.music.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gjl.music.model.WatchRule;
import com.gjl.music.model.WatchStep;
import com.gjl.music.infra.pipeline.graph.GraphNode;
import com.gjl.music.infra.pipeline.graph.PipelineGraph;
import com.gjl.music.infra.pipeline.template.TopologyBuilder;
import com.gjl.music.mapper.WatchRuleMapper;
import com.gjl.music.service.song.PipelineService;
import com.gjl.music.watch.AutoTaskScheduler;
import com.gjl.music.watch.DataPipelineTriggerEvent;
import com.gjl.music.watch.FileChangesDetectedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 自动任务规则服务 — CRUD + 调度注册 + PipelineGraph 构建 + 批量提交。
 *
 * <p>v2 重构：移除 autoRuleCache 和 WatchRuleMatcher（每任务独立扫描路径，无需匹配），
 * 集成 {@link AutoTaskScheduler} 管理每任务调度生命周期。
 *
 * <p>步骤 JSON 示例：
 * <pre>{@code
 * [
 *   { "name": "encoding-repair" },
 *   { "name": "enrich", "config": { "provider": "qqmusic,kugou", "matchMode": "LOOSE" } },
 *   { "name": "fingerprint" },
 *   { "name": "organize", "config": { "pattern": "artist/album", "mode": "move" } }
 * ]
 * }</pre>
 */
@Slf4j
@Service
public class WatchRuleService {

    private final WatchRuleMapper mapper;
    private final PipelineService pipelineService;
    private final AutoTaskScheduler scheduler;
    private final ObjectMapper json = new ObjectMapper();

    public WatchRuleService(WatchRuleMapper mapper,
                            PipelineService pipelineService,
                            AutoTaskScheduler scheduler) {
        this.mapper = mapper;
        this.pipelineService = pipelineService;
        this.scheduler = scheduler;
    }

    // ═══════════════════════════════════════════════════════════════
    // CRUD
    // ═══════════════════════════════════════════════════════════════

    public List<WatchRule> listAll() {
        List<WatchRule> list = mapper.selectAll();
        for (WatchRule r : list) r.setSteps(parseSteps(r.getStepsJson()));
        return list;
    }

    public WatchRule getById(Long id) {
        WatchRule r = mapper.selectById(id);
        if (r != null) r.setSteps(parseSteps(r.getStepsJson()));
        return r;
    }

    public WatchRule create(WatchRule rule) {
        rule.setStepsJson(toJson(rule.getSteps()));
        mapper.insert(rule);
        log.info("WatchRule 创建: id={}, name={}, dirScan={}/{}s, fileScan={}/{}s",
                rule.getId(), rule.getName(),
                rule.isDirScanEnabled(), rule.getDirScanIntervalSec(),
                rule.isFileScanEnabled(), rule.getFileScanIntervalSec());
        // 注册到调度器
        scheduler.registerTask(rule);
        return rule;
    }

    public WatchRule update(WatchRule rule) {
        rule.setStepsJson(toJson(rule.getSteps()));
        mapper.update(rule);
        log.info("WatchRule 更新: id={}, name={}", rule.getId(), rule.getName());
        // 通知调度器更新
        scheduler.updateTask(rule);
        return rule;
    }

    public void delete(Long id) {
        // 先注销调度
        scheduler.unregisterTask(id);
        mapper.deleteById(id);
        log.info("WatchRule 删除: id={}", id);
    }

    /** 仅更新运行状态（轻量写入，由 AutoTaskRunner 调用） */
    public void updateState(Long id, String state, LocalDateTime lastScanAt,
                            LocalDateTime lastRunAt, String errorMessage) {
        mapper.updateState(id, state, lastScanAt, lastRunAt, errorMessage);
    }

    /**
     * 监听 {@link FileChangesDetectedEvent}，为有处理步骤的文件驱动型任务提交管道。
     * 仅处理 song 类任务（artist/album 任务通过 {@link DataPipelineTriggerEvent} 驱动）。
     */
    @EventListener
    public void onFileChangesDetected(FileChangesDetectedEvent event) {
        if (!event.hasProcessingFiles()) return;
        WatchRule task = getById(event.taskId());
        if (task == null || task.getSteps().isEmpty()) return;

        // 只处理 song 类任务（文件驱动），artist/album 跳过
        if (!"song".equals(detectCategory(task.getSteps()))) return;

        List<Path> toProcess = new ArrayList<>();
        toProcess.addAll(event.newFiles());
        toProcess.addAll(event.modFiles());

        try {
            String pipelineId = submitBatch(task.getId(), toProcess);
            if (pipelineId != null) {
                log.info("任务 '{}' 提交管道 {}: {} 个文件 (via event)", task.getName(), pipelineId, toProcess.size());
            }
        } catch (Exception e) {
            log.error("任务 '{}' 提交管道失败 (via event)", task.getName(), e);
        }
    }

    /**
     * 监听 {@link DataPipelineTriggerEvent}，为数据驱动型任务（artist/album）定期执行管道。
     * 跳过纯合并任务（merge 需要前端提供 groups）。
     */
    @EventListener
    public void onDataPipelineTrigger(DataPipelineTriggerEvent event) {
        WatchRule task = getById(event.taskId());
        if (task == null || task.getSteps().isEmpty()) return;

        String category = detectCategory(task.getSteps());
        if ("song".equals(category)) return; // 文件驱动型不处理

        // 跳过纯合并任务（需要外部提供 merge.groups）
        boolean mergeOnly = task.getSteps().stream()
                .allMatch(s -> s.getName().endsWith("-merge"));
        if (mergeOnly) {
            log.debug("任务 '{}' 为纯合并任务，跳过数据驱动调度", task.getName());
            return;
        }

        try {
            String pipelineId = submitDataDrivenPipeline(task, category);
            if (pipelineId != null) {
                log.info("任务 '{}' 提交数据管道 {}: category={} (via event)",
                        task.getName(), pipelineId, category);
            }
        } catch (Exception e) {
            log.error("任务 '{}' 提交数据管道失败 (via event)", task.getName(), e);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Pipeline 构建与提交
    // ═══════════════════════════════════════════════════════════════

    /**
     * 批量提交文件到指定规则的管道处理。
     */
    public String submitBatch(Long ruleId, List<Path> filePaths) {
        WatchRule rule = getById(ruleId);
        if (rule == null) {
            throw new IllegalArgumentException("WatchRule not found: " + ruleId);
        }
        if (rule.getSteps().isEmpty()) {
            log.debug("规则 '{}' 未配置步骤，跳过", rule.getName());
            return null;
        }
        if (filePaths.isEmpty()) {
            log.debug("文件列表为空，跳过提交");
            return null;
        }

        Map<String, Object> options = buildOptions(rule);
        PipelineGraph graph = buildPipelineGraph(rule);

        String[] pathArray = filePaths.stream()
                .map(Path::toString)
                .toArray(String[]::new);

        log.info("提交管道 '{}': {} 个文件, {} 个步骤: {}",
                rule.getName(), filePaths.size(), rule.getSteps().size(),
                rule.getSteps().stream().map(WatchStep::getName).toList());

        return pipelineService.submitDagPipeline(graph, pathArray, options);
    }

    // ═══════════════════════════════════════════════════════════════
    // PipelineGraph 构建
    // ═══════════════════════════════════════════════════════════════

    /**
     * 从 WatchRule 构建 PipelineGraph。
     * <p>根据首个步骤的 category 选择 scanner：
     * <ul>
     *   <li>song → scanner（文件驱动）</li>
     *   <li>artist → artist-scanner（数据驱动）</li>
     *   <li>album → album-scanner（数据驱动）</li>
     * </ul>
     * <p>拓扑：scanner → step1 → step2 → ... 最后一个节点为 finalNode。
     */
    PipelineGraph buildPipelineGraph(WatchRule rule) {
        List<WatchStep> steps = rule.getSteps();
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("规则 '" + rule.getName() + "' 没有步骤");
        }

        String category = detectCategory(steps);
        String scannerName = switch (category) {
            case "artist" -> "artist-scanner";
            case "album"  -> "album-scanner";
            default       -> "scanner";
        };

        List<GraphNode> nodes = new ArrayList<>();
        nodes.add(GraphNode.builder(scannerName).moduleName(scannerName).build());

        String prevId = scannerName;
        for (int i = 0; i < steps.size(); i++) {
            WatchStep step = steps.get(i);
            String nodeId = step.getName() + "_" + i;
            GraphNode.Builder b = GraphNode.builder(nodeId)
                    .moduleName(step.getName())
                    .dependsOn(prevId);
            if (i == steps.size() - 1) {
                b.finalNode(true);
            }
            nodes.add(b.build());
            prevId = nodeId;
        }

        return TopologyBuilder.mixed(nodes);
    }

    /**
     * 从规则步骤构建 options Map。
     */
    Map<String, Object> buildOptions(WatchRule rule) {
        Map<String, Object> options = new LinkedHashMap<>();
        for (WatchStep step : rule.getSteps()) {
            if (!step.getConfig().isEmpty()) {
                options.put(step.getName(), new LinkedHashMap<>(step.getConfig()));
            }
        }
        return options;
    }

    /**
     * 从首个步骤的模块名推断分类。
     *
     * @return "song" / "artist" / "album"
     */
    String detectCategory(List<WatchStep> steps) {
        if (steps == null || steps.isEmpty()) return "song";
        String firstName = steps.get(0).getName();
        if (firstName == null) return "song";
        if (firstName.startsWith("artist-")) return "artist";
        if (firstName.startsWith("album-")) return "album";
        return "song";
    }

    /**
     * 提交数据驱动管道（artist/album 任务，无文件路径）。
     * <p>从首步骤 config 提取 {@code _selection} 作为 scanner 选择条件，
     * 默认 {@code mode=all}。scanner 模块读取 slot 从 DB 查询实体列表。
     */
    private String submitDataDrivenPipeline(WatchRule rule, String category) {
        PipelineGraph graph = buildPipelineGraph(rule);
        Map<String, Object> slots = new LinkedHashMap<>();

        // 从首步骤 config 提取 _selection，默认 mode=all
        Map<String, Object> selection = extractSelection(rule.getSteps());
        if (selection.isEmpty()) {
            selection = Map.of("mode", "all");
        }
        if ("artist".equals(category)) {
            slots.put("artist.selection", selection);
        } else if ("album".equals(category)) {
            slots.put("album.selection", selection);
        }

        Map<String, Object> options = buildOptions(rule);
        if (!options.isEmpty()) {
            slots.put("options", options);
        }

        log.info("提交数据驱动管道 '{}': {} 个步骤, category={}, selection={}",
                rule.getName(), rule.getSteps().size(), category, selection);

        return pipelineService.submitDagPipeline(graph, new String[0], slots);
    }

    /**
     * 从步骤列表首步骤的 config 中提取 {@code _selection} 配置。
     * {@code _selection} 会被移除（不传递给模块的 configure()）。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractSelection(List<WatchStep> steps) {
        if (steps == null || steps.isEmpty()) return Map.of();
        WatchStep first = steps.get(0);
        Map<String, Object> config = first.getConfig();
        if (config == null || config.isEmpty()) return Map.of();
        Object sel = config.get("_selection");
        if (sel instanceof Map<?, ?> m) {
            // 移除 _selection，避免传给模块 configure()
            config.remove("_selection");
            return (Map<String, Object>) m;
        }
        return Map.of();
    }

    // ═══════════════════════════════════════════════════════════════
    // JSON 解析
    // ═══════════════════════════════════════════════════════════════

    private List<WatchStep> parseSteps(String stepsJson) {
        if (stepsJson == null || stepsJson.isBlank()) return List.of();
        try {
            return json.readValue(stepsJson, new TypeReference<List<WatchStep>>() {});
        } catch (Exception e) {
            log.error("解析 steps_json 失败: {}", stepsJson, e);
            return List.of();
        }
    }

    private String toJson(List<WatchStep> steps) {
        if (steps == null || steps.isEmpty()) return "[]";
        try {
            return json.writeValueAsString(steps);
        } catch (Exception e) {
            log.error("序列化 steps 失败", e);
            return "[]";
        }
    }
}
