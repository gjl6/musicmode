package com.gjl.music.infra.pipeline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 节点执行结果 —— 含逐文件 ItemResult 和跨节点数据传递的 outputs。
 */
public class NodeResult {

    /**
     * 单个文件/条目的处理结果。
     */
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

    /** 添加一个文件级处理结果（线程安全） */
    public void addItemResult(String itemKey, boolean success, String errorMessage) {
        itemResults.add(new ItemResult(itemKey, success, errorMessage));
    }

    /** 添加一个跨节点输出（供下游节点通过 NodeContext.getSlot 读取） */
    public void addOutput(String key, Object value) {
        outputs.put(key, value);
    }

    /**
     * 获取所有条目结果（返回快照副本，防止调用方迭代时并发修改）。
     * 调用方通常在单线程上下文中读取（如 PipelineEngine 的最终节点处理），
     * 此处返回副本确保安全。
     */
    public List<ItemResult> getItemResults() {
        synchronized (itemResults) {
            return List.copyOf(itemResults);
        }
    }

    /** 获取跨节点输出 */
    public Map<String, Object> getOutputs() {
        synchronized (outputs) {
            return new LinkedHashMap<>(outputs);
        }
    }

    /** 统计成功数 */
    public int successCount() {
        synchronized (itemResults) {
            return (int) itemResults.stream().filter(ItemResult::success).count();
        }
    }

    /** 统计失败数 */
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
