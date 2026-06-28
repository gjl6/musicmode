package com.gjl.music.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 监控处理步骤 —— 对应 steps_json 中的单个步骤定义。
 *
 * <pre>
 * { "name": "enrich", "config": { "provider": "qqmusic", "matchMode": "LOOSE" } }
 * </pre>
 */
public class WatchStep {

    private String name;
    private Map<String, Object> config = Collections.emptyMap();

    public WatchStep() {}

    public WatchStep(String name, Map<String, Object> config) {
        this.name = name;
        this.config = config != null ? new LinkedHashMap<>(config) : Collections.emptyMap();
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Map<String, Object> getConfig() { return config; }
    @SuppressWarnings("unchecked")
    public void setConfig(Map<String, Object> config) {
        this.config = config != null ? new LinkedHashMap<>(config) : Collections.emptyMap();
    }

    @Override
    public String toString() {
        return "WatchStep{name=" + name + ", config=" + config + '}';
    }
}
