package com.gjl.music.pipeline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public class NodeResult {


    public record ItemResult(String itemKey, boolean success, String errorMessage) {
        public static ItemResult success(String itemKey) {
            return new ItemResult(itemKey, true, null);
        }

        public static ItemResult failed(String itemKey, String errorMessage) {
            return new ItemResult(itemKey, false, errorMessage);
        }
    }

    private final List<ItemResult> itemResults = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, Object> outputs = Collections.synchronizedMap(new LinkedHashMap<>());


    public void addItemResult(String itemKey, boolean success, String errorMessage) {
        itemResults.add(new ItemResult(itemKey, success, errorMessage));
    }


    public void addOutput(String key, Object value) {
        outputs.put(key, value);
    }


    public List<ItemResult> getItemResults() {
        synchronized (itemResults) {
            return List.copyOf(itemResults);
        }
    }


    public Map<String, Object> getOutputs() {
        synchronized (outputs) {
            return new LinkedHashMap<>(outputs);
        }
    }


    public int successCount() {
        synchronized (itemResults) {
            return (int) itemResults.stream().filter(ItemResult::success).count();
        }
    }


    public int failedCount() {
        synchronized (itemResults) {
            return (int) itemResults.stream().filter(r -> !r.success()).count();
        }
    }

    @Override
    public String toString() {
        synchronized (itemResults) {
            return "NodeResult{items=" + itemResults.size()
                    + ", success=" + itemResults.stream().filter(ItemResult::success).count()
                    + ", failed=" + itemResults.stream().filter(r -> !r.success()).count()
                    + ", outputs=" + outputs.keySet() + '}';
        }
    }
}
