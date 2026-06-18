package com.gjl.music.pipeline.template;

import com.gjl.music.pipeline.graph.PipelineGraph;
import com.gjl.music.pipeline.NodeContext;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;


@Slf4j
public class TemplateRegistry {

    private final Map<String, TemplateEntry> templates = new LinkedHashMap<>();


    public void register(String name, PipelineGraph graph, Consumer<NodeContext> initializer) {
        templates.put(name, new TemplateEntry(name, graph, initializer));
        log.debug("Template registered: {}", name);
    }


    public void register(String name, PipelineGraph graph) {
        register(name, graph, null);
    }


    public PipelineGraph getGraph(String name) {
        TemplateEntry entry = templates.get(name);
        if (entry == null) {
            throw new IllegalArgumentException("Unknown template: " + name);
        }
        return entry.graph();
    }


    public Consumer<NodeContext> getInitializer(String name) {
        TemplateEntry entry = templates.get(name);
        return entry != null ? entry.initializer() : null;
    }


    public Set<String> names() {
        return templates.keySet();
    }


    public boolean contains(String name) {
        return templates.containsKey(name);
    }


    private record TemplateEntry(String name, PipelineGraph graph, Consumer<NodeContext> initializer) {}
}
