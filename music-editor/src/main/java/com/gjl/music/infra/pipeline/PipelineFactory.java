package com.gjl.music.infra.pipeline;

import com.gjl.music.infra.pipeline.engine.FileProgressTracker;
import com.gjl.music.infra.pipeline.engine.PauseController;
import com.gjl.music.infra.pipeline.engine.PipelineEngine;
import com.gjl.music.infra.pipeline.graph.GraphNode;
import com.gjl.music.infra.pipeline.graph.PipelineGraph;
import com.gjl.music.infra.pipeline.template.TemplateRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * 管道工厂 —— 组装 PipelineEngine，管理 handlerRegistry 和模板注册。
 *
 * <p>所有模块实现 NodeHandler 后在此注册，模板在 TemplateRegistry 中定义。
 */
@Slf4j
@Component
public class PipelineFactory {

    private final Executor workerExecutor;
    private final Executor virtualExecutor;
    private final PipelinePersistence persistence;
    private final TemplateRegistry templateRegistry = new TemplateRegistry();

    @org.springframework.beans.factory.annotation.Value("${music.root-dir}")
    private String rootDir;

    /** 所有已注册的 NodeHandler（模块名 → 处理器） */
    private final Map<String, NodeHandler> handlerRegistry = new ConcurrentHashMap<>();

    public PipelineFactory(@Qualifier("moduleParallelExecutor") Executor workerExecutor,
                           @Qualifier("virtualThreadExecutor") Executor virtualExecutor,
                           PipelinePersistence persistence) {
        this.workerExecutor = workerExecutor;
        this.virtualExecutor = virtualExecutor;
        this.persistence = persistence;
    }

    // ── Handler 注册 ──

    /** 注册一个模块处理器 */
    public void registerHandler(String moduleName, NodeHandler handler) {
        handlerRegistry.put(moduleName, handler);
        log.debug("Handler registered: {}", moduleName);
    }

    /** 批量注册 */
    public void registerHandlers(Map<String, NodeHandler> handlers) {
        handlerRegistry.putAll(handlers);
    }

    /** 获取已注册的模块名 */
    public Set<String> moduleNames() {
        return Collections.unmodifiableSet(handlerRegistry.keySet());
    }

    /** 按名称获取 handler */
    public NodeHandler getHandler(String name) {
        return handlerRegistry.get(name);
    }

    // ── 模板 ──

    /** 注册模板 */
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

    // ── 创建 PipelineEngine ──

    /**
     * 从模板创建 PipelineEngine。
     */
    public PipelineEngine createFromTemplate(String templateName, String pipelineId,
                                              Consumer<NodeContext> extraInit,
                                              Consumer<com.gjl.music.model.PipelineItemLog> onItemComplete) {
        PipelineGraph graph = templateRegistry.getGraph(templateName);
        Consumer<NodeContext> init = templateRegistry.getInitializer(templateName);

        // 组合 template 初始化器 + 调用方初始化器
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

        // 设置 graph_json 的序列化表示
        graphJsonCache.put(pipelineId, persistence.toJson(buildGraphDef(graph)));

        return engine;
    }

    /**
     * 从图定义创建 PipelineEngine。
     */
    public PipelineEngine createFromGraph(PipelineGraph graph, String pipelineId) {
        return createFromGraph(graph, pipelineId, null, null);
    }

    /**
     * 从图定义创建 PipelineEngine，可附带 context 初始化器。
     */
    public PipelineEngine createFromGraph(PipelineGraph graph, String pipelineId,
                                          Consumer<NodeContext> contextInitializer) {
        return createFromGraph(graph, pipelineId, contextInitializer, null);
    }

    /**
     * 从图定义创建 PipelineEngine，可附带 context 初始化器和文件级回调。
     */
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

    // ── Graph JSON 缓存（供持久化使用）──

    private final Map<String, String> graphJsonCache = new ConcurrentHashMap<>();

    public String getGraphJson(String pipelineId) {
        return graphJsonCache.get(pipelineId);
    }

    public void removeGraphJson(String pipelineId) {
        graphJsonCache.remove(pipelineId);
    }

    /**
     * 从 DB 中存储的 graphJson 重建 PipelineEngine（用于崩溃恢复）。
     */
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

        // 确保至少有一个 finalNode
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

    // ── 工具方法 ──

    /** 将用户相对路径解析为文件系统绝对路径（使用注入的 rootDir） */
    public Path resolvePath(String userPath) {
        return resolvePath(userPath, rootDir);
    }

    /** 将用户相对路径解析为文件系统绝对路径 */
    public Path resolvePath(String userPath, String rootDir) {
        Path p = Path.of(userPath);
        if (p.isAbsolute()) {
            // 有盘符 = Windows 真正绝对路径（如 D:\...），直接返回
            if (userPath.length() >= 2 && userPath.charAt(1) == ':') {
                return p.normalize().toAbsolutePath();
            }
            // 以 "/" 开头 = Linux 下被误判为绝对路径的前端相对路径（如 /2, /music2/宝石Gem）
            // 去掉 "/" 前缀后拼到 rootDir，确保 Windows/Linux 行为一致
            String stripped = userPath;
            while (stripped.startsWith("/")) {
                stripped = stripped.substring(1);
            }
            if (stripped.isEmpty()) {
                return Path.of(rootDir).toAbsolutePath();
            }
            return Path.of(rootDir, stripped).normalize().toAbsolutePath();
        }
        return Path.of(rootDir, userPath).normalize().toAbsolutePath();
    }
}
