package com.gjl.music.infra.pipeline.template;

import com.gjl.music.infra.pipeline.graph.PipelineGraph;
import com.gjl.music.infra.pipeline.NodeContext;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 统一模板注册中心 —— 所有管道模板在此注册。
 *
 * <p>每个模板 = PipelineGraph（DAG 拓扑）+ Initializer（可选，设置初始 context slots）。
 *
 * <h3>预注册模板</h3>
 * <pre>
 * browse         → filesystem
 * browse-parse   → filesystem → parser → db-operator → db-sync
 * parse          → filesystem → parser → db-operator → db-sync (单文件)
 * write          → filesystem → client-metadata → db-operator → db-sync → writer → db-operator
 * repair         → scanner → {db-sync, encoding-repair}
 * convert        → scanner → {db-sync, chinese-convert}
 * enrich         → scanner → {db-sync, enrich}
 * fingerprint    → scanner → {db-sync, fingerprint}
 * scan-fingerprint → 同上
 * dedup          → scanner → {db-sync, dedup}
 * split          → scanner → {db-sync, split-metadata}
 * replace        → scanner → {db-sync, replace-text}
 * </pre>
 */
@Slf4j
public class TemplateRegistry {

    private final Map<String, TemplateEntry> templates = new LinkedHashMap<>();

    /**
     * 注册一个模板。
     *
     * @param name        模板名
     * @param graph       管道拓扑
     * @param initializer 可选的初始化回调（设置 SLOT_INPUT_PATHS, SLOT_ROOT_DIR 等）
     */
    public void register(String name, PipelineGraph graph, Consumer<NodeContext> initializer) {
        templates.put(name, new TemplateEntry(name, graph, initializer));
        log.debug("Template registered: {}", name);
    }

    /** 注册无初始化器的模板 */
    public void register(String name, PipelineGraph graph) {
        register(name, graph, null);
    }

    /** 获取模板图 */
    public PipelineGraph getGraph(String name) {
        TemplateEntry entry = templates.get(name);
        if (entry == null) {
            throw new IllegalArgumentException("Unknown template: " + name);
        }
        return entry.graph();
    }

    /** 获取模板初始化器 */
    public Consumer<NodeContext> getInitializer(String name) {
        TemplateEntry entry = templates.get(name);
        return entry != null ? entry.initializer() : null;
    }

    /** 获取所有模板名 */
    public Set<String> names() {
        return templates.keySet();
    }

    /** 模板是否存在 */
    public boolean contains(String name) {
        return templates.containsKey(name);
    }

    // ── 内部 ──

    private record TemplateEntry(String name, PipelineGraph graph, Consumer<NodeContext> initializer) {}
}
