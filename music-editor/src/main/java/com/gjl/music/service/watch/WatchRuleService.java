package com.gjl.music.service.watch;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gjl.music.model.WatchRule;
import com.gjl.music.model.WatchStep;
import com.gjl.music.pipeline.graph.GraphNode;
import com.gjl.music.pipeline.graph.PipelineGraph;
import com.gjl.music.pipeline.template.TopologyBuilder;
import com.gjl.music.mapper.WatchRuleMapper;
import com.gjl.music.service.PipelineService;
import com.gjl.music.watch.WatchRuleMatcher;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


@Slf4j
@Service
public class WatchRuleService {

    private final WatchRuleMapper mapper;
    private final PipelineService pipelineService;
    private final ObjectMapper json = new ObjectMapper();


    private final Map<Long, WatchRule> autoRuleCache = new ConcurrentHashMap<>();

    public WatchRuleService(WatchRuleMapper mapper,
                            PipelineService pipelineService) {
        this.mapper = mapper;
        this.pipelineService = pipelineService;
    }

    @PostConstruct
    void refreshCache() {
        autoRuleCache.clear();
        List<WatchRule> rules = mapper.selectAutoTrigger();
        for (WatchRule r : rules) {
            r.setSteps(parseSteps(r.getStepsJson()));
            autoRuleCache.put(r.getId(), r);
        }
        log.info("WatchRule 缓存已加载: {} 条自动触发规则", autoRuleCache.size());
    }


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
        refreshCache();
        log.info("WatchRule 创建: id={}, name={}", rule.getId(), rule.getName());
        return rule;
    }

    public WatchRule update(WatchRule rule) {
        rule.setStepsJson(toJson(rule.getSteps()));
        mapper.update(rule);
        refreshCache();
        log.info("WatchRule 更新: id={}, name={}", rule.getId(), rule.getName());
        return rule;
    }

    public void delete(Long id) {
        mapper.deleteById(id);
        refreshCache();
        log.info("WatchRule 删除: id={}", id);
    }


    public WatchRule findMatching(String relativePath) {
        return WatchRuleMatcher.findMatching(autoRuleCache.values(), relativePath);
    }


    public Collection<WatchRule> getAutoRules() {
        return Collections.unmodifiableCollection(autoRuleCache.values());
    }


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

        log.info("提交 WatchRule '{}': {} 个文件, {} 个步骤: {}",
                rule.getName(), filePaths.size(), rule.getSteps().size(),
                rule.getSteps().stream().map(WatchStep::getName).toList());

                return pipelineService.submitDagPipeline(graph, pathArray, options);
    }


    PipelineGraph buildPipelineGraph(WatchRule rule) {
        List<WatchStep> steps = rule.getSteps();
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("规则 '" + rule.getName() + "' 没有步骤");
        }

        List<GraphNode> nodes = new ArrayList<>();

                nodes.add(GraphNode.builder("scanner").moduleName("scanner").build());

                String prevId = "scanner";
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


    Map<String, Object> buildOptions(WatchRule rule) {
        Map<String, Object> options = new LinkedHashMap<>();
        for (WatchStep step : rule.getSteps()) {
            if (!step.getConfig().isEmpty()) {
                options.put(step.getName(), new LinkedHashMap<>(step.getConfig()));
            }
        }
        return options;
    }


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
