package com.gjl.music.pipeline;

import com.gjl.music.pipeline.engine.FileProgressTracker;
import com.gjl.music.pipeline.engine.PauseController;
import com.gjl.music.pipeline.engine.PipelineEngine;
import com.gjl.music.pipeline.graph.GraphNode;
import com.gjl.music.pipeline.graph.PipelineGraph;
import com.gjl.music.pipeline.template.TemplateRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;


@Slf4j
@Component
public class PipelineFactory {

    private final Executor workerExecutor;
    private final Executor virtualExecutor;
    private final PipelinePersistence persistence;
    private final TemplateRegistry templateRegistry = new TemplateRegistry();

    @org.springframework.beans.factory.annotation.Value("${music.root-dir}")
    private String rootDir;


    private final Map<String, NodeHandler> handlerRegistry = new ConcurrentHashMap<>();

    public PipelineFactory(@Qualifier("moduleParallelExecutor") Executor workerExecutor,
                           @Qualifier("virtualThreadExecutor") Executor virtualExecutor,
                           PipelinePersistence persistence) {
        this.workerExecutor = workerExecutor;
        this.virtualExecutor = virtualExecutor;
        this.persistence = persistence;
    }


    public void registerHandler(String moduleName, NodeHandler handler) {
        handlerRegistry.put(moduleName, handler);
        log.debug("Handler registered: {}", moduleName);
    }


    public void registerHandlers(Map<String, NodeHandler> handlers) {
        handlerRegistry.putAll(handlers);
    }


    public Set<String> moduleNames() {
        return Collections.unmodifiableSet(handlerRegistry.keySet());
    }


    public NodeHandler getHandler(String name) {
        return handlerRegistry.get(name);
    }


    public void registerTemplate(String name, PipelineGraph graph,
                                  Consumer<NodeContext> initializer) {
        templateRegistry.register(name, graph, initializer);
    }

    public void registerTemplate(String name, PipelineGraph graph) {
        templateRegistry.register(name, graph);
    }

    public TemplateRegistry getTemplateRegistry() {
        return templateRegistry;
    }

    public Set<String> templateNames() {
        return templateRegistry.names();
    }


    public PipelineEngine createFromTemplate(String templateName, String pipelineId,
                                              Consumer<NodeContext> extraInit,
                                              Consumer<com.gjl.music.model.PipelineItemLog> onItemComplete) {
        PipelineGraph graph = templateRegistry.getGraph(templateName);
        Consumer<NodeContext> init = templateRegistry.getInitializer(templateName);

                Consumer<NodeContext> composed = ctx -> {
            if (init != null) init.accept(ctx);
            if (extraInit != null) extraInit.accept(ctx);
        };

        FileProgressTracker tracker = new FileProgressTracker(pipelineId);
        PauseController pauseCtrl = new PauseController();

        PipelineEngine engine = new PipelineEngine(
                pipelineId, graph, new HashMap<>(handlerRegistry),
                tracker, pauseCtrl, workerExecutor, virtualExecutor,
                (pid, itemKey, success, errorNode, errorMsg) -> {
                    var log = new com.gjl.music.model.PipelineItemLog();
                    log.setPipelineId(pid);
                    log.setItemKey(itemKey);
                    log.setStatus(success ? "SUCCESS" : "FAILED");
                    log.setErrorNode(errorNode);
                    log.setErrorMsg(errorMsg);
                    persistence.bufferItemLog(log);
                    if (onItemComplete != null) onItemComplete.accept(log);
                },
                composed);

                graphJsonCache.put(pipelineId, persistence.toJson(buildGraphDef(graph)));

        return engine;
    }


    public PipelineEngine createFromGraph(PipelineGraph graph, String pipelineId) {
        return createFromGraph(graph, pipelineId, null, null);
    }


    public PipelineEngine createFromGraph(PipelineGraph graph, String pipelineId,
                                          Consumer<NodeContext> contextInitializer) {
        return createFromGraph(graph, pipelineId, contextInitializer, null);
    }


    public PipelineEngine createFromGraph(PipelineGraph graph, String pipelineId,
                                          Consumer<NodeContext> contextInitializer,
                                          Consumer<com.gjl.music.model.PipelineItemLog> onItemComplete) {
        FileProgressTracker tracker = new FileProgressTracker(pipelineId);
        PauseController pauseCtrl = new PauseController();

        PipelineEngine engine = new PipelineEngine(
                pipelineId, graph, new HashMap<>(handlerRegistry),
                tracker, pauseCtrl, workerExecutor, virtualExecutor,
                (pid, itemKey, success, errorNode, errorMsg) -> {
                    var log = new com.gjl.music.model.PipelineItemLog();
                    log.setPipelineId(pid);
                    log.setItemKey(itemKey);
                    log.setStatus(success ? "SUCCESS" : "FAILED");
                    log.setErrorNode(errorNode);
                    log.setErrorMsg(errorMsg);
                    persistence.bufferItemLog(log);
                    if (onItemComplete != null) onItemComplete.accept(log);
                },
                contextInitializer);

        graphJsonCache.put(pipelineId, persistence.toJson(buildGraphDef(graph)));
        return engine;
    }


    private final Map<String, String> graphJsonCache = new ConcurrentHashMap<>();

    public String getGraphJson(String pipelineId) {
        return graphJsonCache.get(pipelineId);
    }

    public void removeGraphJson(String pipelineId) {
        graphJsonCache.remove(pipelineId);
    }


    public PipelineEngine createFromGraphJson(String graphJson, String pipelineId) {
        List<Map<String, Object>> nodeList = persistence.fromJson(graphJson,
                new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});
        if (nodeList == null || nodeList.isEmpty()) {
            throw new IllegalArgumentException("graphJson is empty or invalid");
        }

        List<GraphNode> nodes = new ArrayList<>();
        for (Map<String, Object> raw : nodeList) {
            String id = (String) raw.get("id");
            String moduleName = (String) raw.get("moduleName");

            GraphNode.Builder builder = GraphNode.builder(id)
                    .moduleName(moduleName != null ? moduleName : id);

            @SuppressWarnings("unchecked")
            List<String> deps = (List<String>) raw.getOrDefault("dependencies", List.of());
            builder.dependsOn(deps.toArray(new String[0]));

            @SuppressWarnings("unchecked")
            Map<String, Object> config = (Map<String, Object>) raw.getOrDefault("config", Map.of());
            builder.configAll(config);

            Boolean isFinal = (Boolean) raw.get("finalNode");
            builder.finalNode(isFinal != null ? isFinal : false);

            nodes.add(builder.build());
        }

                if (nodes.stream().noneMatch(GraphNode::isFinalNode) && !nodes.isEmpty()) {
            GraphNode last = nodes.get(nodes.size() - 1);
            nodes.set(nodes.size() - 1, GraphNode.builder(last.getId())
                    .handler(last.getHandler())
                    .dependsOn(last.getDependencies().toArray(new String[0]))
                    .moduleName(last.getModuleName())
                    .configAll(last.getConfig())
                    .finalNode(true)
                    .build());
        }

        PipelineGraph graph = new PipelineGraph(nodes);
        return createFromGraph(graph, pipelineId);
    }

    private List<Map<String, Object>> buildGraphDef(PipelineGraph graph) {
        List<Map<String, Object>> defs = new ArrayList<>();
        for (GraphNode node : graph.getNodes().values()) {
            Map<String, Object> def = new LinkedHashMap<>();
            def.put("id", node.getId());
            def.put("moduleName", node.getModuleName());
            def.put("dependencies", node.getDependencies());
            def.put("finalNode", node.isFinalNode());
            if (!node.getConfig().isEmpty()) {
                def.put("config", node.getConfig());
            }
            defs.add(def);
        }
        return defs;
    }


    public Path resolvePath(String userPath) {
        return resolvePath(userPath, rootDir);
    }


    public Path resolvePath(String userPath, String rootDir) {
        Path p = Path.of(userPath);
                if (p.isAbsolute()) return p.normalize().toAbsolutePath();
        return Path.of(rootDir, userPath).normalize().toAbsolutePath();
    }
}
