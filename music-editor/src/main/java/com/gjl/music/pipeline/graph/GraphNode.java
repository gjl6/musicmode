package com.gjl.music.pipeline.graph;

import com.gjl.music.pipeline.NodeHandler;

import java.util.*;


public class GraphNode {

    private final String id;
    private final NodeHandler handler;
    private final List<String> dependencies;
    private final boolean finalNode;
    private final String moduleName;
    private final Map<String, Object> config;

    private GraphNode(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "id must not be null");
        this.handler = builder.handler;
        this.dependencies = List.copyOf(builder.dependencies);
        this.finalNode = builder.finalNode;
        this.moduleName = builder.moduleName;
        this.config = Map.copyOf(builder.config);
    }

    public String getId() { return id; }
    public NodeHandler getHandler() { return handler; }
    public List<String> getDependencies() { return dependencies; }
    public boolean isFinalNode() { return finalNode; }
    public String getModuleName() { return moduleName; }
    public Map<String, Object> getConfig() { return config; }

    @Override
    public String toString() {
        return "GraphNode{id=" + id + ", deps=" + dependencies
                + ", final=" + finalNode + ", module=" + moduleName + '}';
    }


    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static class Builder {
        private final String id;
        private NodeHandler handler;
        private final List<String> dependencies = new ArrayList<>();
        private boolean finalNode;
        private String moduleName;
        private final Map<String, Object> config = new LinkedHashMap<>();

        private Builder(String id) { this.id = id; }

        public Builder handler(NodeHandler handler) { this.handler = handler; return this; }
        public Builder dependsOn(String... ids) { dependencies.addAll(Arrays.asList(ids)); return this; }
        public Builder finalNode(boolean v) { this.finalNode = v; return this; }
        public Builder moduleName(String name) { this.moduleName = name; return this; }
        public Builder config(String key, Object value) { config.put(key, value); return this; }
        public Builder configAll(Map<String, Object> map) { config.putAll(map); return this; }
        public GraphNode build() { return new GraphNode(this); }
    }
}
