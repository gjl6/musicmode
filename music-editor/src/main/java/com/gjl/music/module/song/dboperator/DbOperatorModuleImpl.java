package com.gjl.music.module.song.dboperator;

import com.gjl.music.model.*;
import com.gjl.music.module.FailurePolicy;
import com.gjl.music.persistence.MetadataPersister;
import com.gjl.music.infra.pipeline.NodeContext;
import com.gjl.music.infra.pipeline.NodeHandler;
import com.gjl.music.infra.pipeline.NodeResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Component
public class DbOperatorModuleImpl implements DbOperatorModule, NodeHandler {

    private final MetadataPersister persister;
    private final List<Map.Entry<String, MusicMetadata>> buffer =
            Collections.synchronizedList(new ArrayList<>());
    private int batchSize = 50;

    public DbOperatorModuleImpl(MetadataPersister persister) {
        this.persister = persister;
    }

    @Override public String name() { return "db-operator"; }
    @Override public FailurePolicy failurePolicy() { return FailurePolicy.RETRY; }
    @Override public boolean isUserVisible() { return false; }

    @Override
    public void configure(Map<String, Object> options) {
        if (options != null && options.get("batchSize") instanceof Number n) {
            this.batchSize = Math.max(1, n.intValue());
        }
    }

    // ── 领域方法 ──

    @Override
    public void ensureTables() {
        // DDL 由 schema-{platform}.sql 通过 spring.sql.init 管理
    }

    // ── 流式模式（批量缓冲）──

    @SuppressWarnings("unchecked")
    @Transactional
    public Object process(Object input) {
        Map.Entry<?, MusicMetadata> entry = (Map.Entry<?, MusicMetadata>) input;
        String filePath = String.valueOf(entry.getKey());
        buffer.add(Map.entry(filePath, entry.getValue()));
        if (buffer.size() >= batchSize) {
            flushBatch();
        }
        return input; // 转发给下游，不截断数据流
    }

    public List<Object> flush() {
        flushBatch();
        return null;
    }


    private void flushBatch() {
        List<Map.Entry<String, MusicMetadata>> batch;
        synchronized (buffer) {
            if (buffer.isEmpty()) return;
            batch = new ArrayList<>(buffer);
            buffer.clear();
        }
        log.debug("批量写库 {} 首", batch.size());
        persister.persistBatch(batch);
    }

    // ── 批量模式（回退）──

    @Override
    public NodeResult execute(NodeContext ctx) throws Exception {
        NodeResult result = new NodeResult();
        Map<?, MusicMetadata> metadataMap = resolveSource(ctx);
        if (metadataMap == null || metadataMap.isEmpty()) {
            log.warn("没有待入库的元数据，跳过");
            return result;
        }

        log.info("开始批量写入数据库，共 {} 条元数据", metadataMap.size());
        List<Map.Entry<String, MusicMetadata>> batch = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        for (Map.Entry<?, MusicMetadata> entry : metadataMap.entrySet()) {
            String filePath = String.valueOf(entry.getKey());
            MusicMetadata meta = entry.getValue();
            if (meta == null || meta.getImmutableSongs().isEmpty()) {
                skipped.add(filePath);
                result.addItemResult(filePath, false, "无效元数据");
                continue;
            }
            batch.add(Map.entry(filePath, meta));
        }

        if (batch.isEmpty()) {
            log.warn("无有效元数据可入库，跳过 {} 条", skipped.size());
            return result;
        }

        try {
            persister.persistBatch(batch); // → common 层批量持久化
            for (var e : batch) {
                result.addItemResult(e.getKey(), true, null);
            }
            log.info("入库完成，成功 {} 条", batch.size());
        } catch (Exception e) {
            log.error("批量入库失败", e);
            for (var entry : batch) {
                result.addItemResult(entry.getKey(), false, e.getMessage());
            }
        }

        List<String> successFiles = new ArrayList<>();
        List<String> failureFiles = new ArrayList<>();
        for (var item : result.getItemResults()) {
            if (item.success()) successFiles.add(item.itemKey());
            else failureFiles.add(item.itemKey());
        }
        ctx.setSlot("persist.result",
                Map.of("success", successFiles, "failure", failureFiles));
        result.addOutput("persist.result",
                Map.of("success", successFiles, "failure", failureFiles));
        return result;
    }

    /**
     * 解析待入库的元数据 Map，兼容三种路径：
     * <ul>
     *   <li>拓扑节点：{@code processed.metadata} → {@code node.parser.output}</li>
     *   <li>invoke 调用：{@code input}（GapFillingModule 通过 ctx.invoke 传入）</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    private Map<?, MusicMetadata> resolveSource(NodeContext context) {
        Map<?, MusicMetadata> result = context.getSlot("processed.metadata");
        if (result != null && !result.isEmpty()) return result;
        result = context.getSlot("node.parser.output");
        if (result != null && !result.isEmpty()) return result;
        // invoke 路径：数据在 "input" slot
        Object input = context.getSlot("input");
        if (input instanceof Map<?, ?> map && !map.isEmpty()
                && map.values().iterator().next() instanceof MusicMetadata) {
            return (Map<?, MusicMetadata>) map;
        }
        return null;
    }
}
