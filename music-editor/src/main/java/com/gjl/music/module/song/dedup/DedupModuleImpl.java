package com.gjl.music.module.song.dedup;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gjl.music.editor.mapper.PipelineDedupGroupMapper;
import com.gjl.music.model.PipelineDedupGroup;
import com.gjl.music.module.song.dedup.strategy.DedupStrategy;
import com.gjl.music.module.FailurePolicy;
import com.gjl.music.module.song.support.GapFillingModule;
import com.gjl.music.infra.pipeline.NodeContext;
import com.gjl.music.infra.pipeline.NodeResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.*;

/**
 * 重复检测模块实现 —— 4 种独立策略，用户按需选择。
 * 批量模式：从 NodeContext 读取路径，分派给启用的策略，结果持久化到 pipeline_dedup_group 表。
 */
@Slf4j
@Component
public class DedupModuleImpl extends GapFillingModule implements DedupModule {

    private static final Set<String> DEFAULT_STRATEGIES = Set.of("hash", "filename", "metadata");

    private final Map<String, DedupStrategy> strategyMap = new LinkedHashMap<>();
    private final PipelineDedupGroupMapper dedupGroupMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private Set<String> enabledStrategyNames = DEFAULT_STRATEGIES;
    private Map<String, Object> strategyOptions = Map.of();

    public DedupModuleImpl(List<DedupStrategy> strategies,
                           PipelineDedupGroupMapper dedupGroupMapper) {
        for (DedupStrategy s : strategies) {
            strategyMap.put(s.name(), s);
        }
        this.dedupGroupMapper = dedupGroupMapper;
        log.info("DedupModule 注册策略: {}", strategyMap.keySet());
    }

    @Override public String name() { return "dedup"; }
    @Override public FailurePolicy failurePolicy() { return FailurePolicy.SKIP; }
    @Override public String label() { return "去重检测"; }

    @Override
    public List<Map<String, Object>> configSchema() {
        return List.of(
            Map.of("key", "strategies", "label", "去重策略", "type", "multiselect",
                "default", List.of("hash", "filename", "metadata"),
                "options", List.of(
                    Map.of("value", "hash", "label", "哈希比对（文件内容）"),
                    Map.of("value", "filename", "label", "文件名相似度"),
                    Map.of("value", "metadata", "label", "元数据比对"),
                    Map.of("value", "fingerprint", "label", "声纹指纹")
                ))
        );
    }

    @Override
    public void configure(Map<String, Object> options) {
        if (options == null || options.isEmpty()) return;

        // 策略选择
        Object strategiesObj = options.get("strategies");
        if (strategiesObj instanceof List<?> list && !list.isEmpty()) {
            enabledStrategyNames = new LinkedHashSet<>();
            for (Object item : list) {
                String name = String.valueOf(item);
                if (strategyMap.containsKey(name)) {
                    enabledStrategyNames.add(name);
                }
            }
        }

        // 策略专属选项 — 展开嵌套配置（如 fingerprint.threshold → threshold）
        // 前端格式: { fingerprint: { threshold: 0.80 } } → 策略直接读 options.get("threshold")
        strategyOptions = new LinkedHashMap<>();
        for (var entry : options.entrySet()) {
            String key = entry.getKey();
            if ("strategies".equals(key)) continue;
            if (entry.getValue() instanceof Map<?, ?> nested && strategyMap.containsKey(key)) {
                for (var ne : nested.entrySet()) {
                    strategyOptions.put(ne.getKey().toString(), ne.getValue());
                }
            } else {
                strategyOptions.put(key, entry.getValue());
            }
        }
    }

    @Override
    public NodeResult execute(NodeContext ctx) throws Exception {
        // 从上下文数据流加载模块配置（DedupModuleImpl 重写 execute 不走 GapFillingModule 模板，需手动加载）
        Map<String, Object> allOptions = ctx.getSlot("options");
        if (allOptions != null) {
            Object moduleConfig = allOptions.get(name());
            if (moduleConfig instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> config = (Map<String, Object>) map;
                configure(config);
            }
        }

        NodeResult result = new NodeResult();
        List<Path> filePaths = resolveInputPaths(ctx);
        if (filePaths.isEmpty()) {
            log.warn("无法确定去重文件列表，跳过");
            return result;
        }

        log.info("开始重复检测, fileCount={}, 策略={}", filePaths.size(), enabledStrategyNames);

        Map<String, List<DuplicateGroup>> allResults = new LinkedHashMap<>();

        for (String name : enabledStrategyNames) {
            DedupStrategy strategy = strategyMap.get(name);
            if (strategy == null) continue;
            try {
                List<DuplicateGroup> groups = strategy.detect(filePaths, strategyOptions, ctx);
                allResults.put(name, groups);
            } catch (Exception e) {
                log.error("策略 [{}] 执行失败", name, e);
                allResults.put(name, List.of());
            }
        }

        // 写入 context + 持久化到 pipeline_dedup_group 表
        ctx.setSlot("duplicate.groups", allResults);
        result.addOutput("duplicate.groups", allResults);
        persistGroups(ctx.getPipelineId(), allResults);

        int totalGroups = allResults.values().stream().mapToInt(List::size).sum();
        log.info("重复检测完成, 策略={}, 总重复组={}", allResults.keySet(), totalGroups);

        // 为每个文件产出 ItemResult（最终节点必须逐文件报告，否则引擎认为文件丢失）
        for (Path p : filePaths) {
            result.addItemResult(p.toString(), true, null);
        }
        return result;
    }

    /** 将去重分组持久化到 pipeline_dedup_group 表，供前端分页查询 */
    private void persistGroups(String pipelineId, Map<String, List<DuplicateGroup>> allResults) {
        List<PipelineDedupGroup> entities = new ArrayList<>();
        for (var entry : allResults.entrySet()) {
            String strategy = entry.getKey();
            List<DuplicateGroup> groups = entry.getValue();
            for (int i = 0; i < groups.size(); i++) {
                DuplicateGroup g = groups.get(i);
                PipelineDedupGroup entity = new PipelineDedupGroup();
                entity.setPipelineId(pipelineId);
                entity.setStrategy(strategy);
                entity.setGroupIndex(i);
                try {
                    entity.setFilePaths(objectMapper.writeValueAsString(g.getFilePaths()));
                    entity.setFileNames(objectMapper.writeValueAsString(g.getFileNames()));
                } catch (JsonProcessingException e) {
                    log.warn("序列化重复组失败：{}", e.getMessage());
                    continue;
                }
                entities.add(entity);
            }
        }
        if (!entities.isEmpty()) {
            try {
                dedupGroupMapper.batchInsert(entities);
                log.info("持久化 {} 个重复组到 pipeline_dedup_group", entities.size());
            } catch (Exception e) {
                log.error("持久化去重分组失败", e);
            }
        }
    }
}
