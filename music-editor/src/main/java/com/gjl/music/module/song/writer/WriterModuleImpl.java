package com.gjl.music.module.song.writer;

import com.gjl.music.exception.MetadataWriteException;
import com.gjl.music.model.MusicMetadata;
import com.gjl.music.module.FailurePolicy;
import com.gjl.music.infra.pipeline.NodeContext;
import com.gjl.music.infra.pipeline.NodeHandler;
import com.gjl.music.infra.pipeline.NodeResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
public class WriterModuleImpl implements WriterModule, NodeHandler {

    private final WriterFactory writerFactory;

    public WriterModuleImpl(WriterFactory writerFactory) {
        this.writerFactory = writerFactory;
    }

    @Override public String name() { return "writer"; }
    @Override public FailurePolicy failurePolicy() { return FailurePolicy.SKIP; }
    @Override public boolean isUserVisible() { return false; }

    @Override
    public void configure(Map<String, Object> options) {
        // 无配置项
    }

    // ── 流式模式（多线程并行写入）──

    @SuppressWarnings("unchecked")
    public Object process(Object input) {
        Map.Entry<?, ?> raw = (Map.Entry<?, ?>) input;
        MusicMetadata meta = (MusicMetadata) raw.getValue();
        Path path = resolvePath(raw.getKey(), meta);
        try {
            writerFactory.write(path.toFile(), meta);
            return Map.entry(path, meta);
        } catch (MetadataWriteException | RuntimeException e) {
            log.error("写标签失败: {} - {}", path.getFileName(), e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public List<Object> flush() {
        return null;
    }

    // ── 批量模式（回退）──

    @SuppressWarnings("unchecked")
    @Override
    public NodeResult execute(NodeContext ctx) throws Exception {
        NodeResult nodeResult = new NodeResult();
        Map<?, MusicMetadata> metaMap = resolveSource(ctx);
        if (metaMap.isEmpty()) {
            ctx.setSlot("write.result", new WriteResult());
            nodeResult.addOutput("write.result", new WriteResult());
            return nodeResult;
        }

        log.info("开始写回 {} 个文件的元数据", metaMap.size());
        WriteResult result = new WriteResult();

        for (Map.Entry<?, MusicMetadata> entry : metaMap.entrySet()) {
            Path path = resolvePath(entry.getKey(), entry.getValue());
            try {
                writerFactory.write(path.toFile(), entry.getValue());
                result.addSuccess(path.toString());
                nodeResult.addItemResult(path.toString(), true, null);
            } catch (MetadataWriteException | RuntimeException e) {
                log.error("写入失败: {} - {}", path.getFileName(), e.getMessage());
                result.addFailure(path.toString());
                nodeResult.addItemResult(path.toString(), false, e.getMessage());
            }
        }

        ctx.setSlot("write.result", result);
        nodeResult.addOutput("write.result", result);
        log.info("写入完成: {}", result);
        return nodeResult;
    }

    /**
     * 解析待写入的元数据 Map，兼容三种路径：
     * <ul>
     *   <li>拓扑节点：{@code processed.metadata} → {@code node.parser.output}</li>
     *   <li>invoke 调用：{@code input}（GapFillingModule 通过 ctx.invoke 传入）</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    private Map<?, MusicMetadata> resolveSource(NodeContext ctx) {
        Map<?, MusicMetadata> m = ctx.getSlot("processed.metadata");
        if (m != null && !m.isEmpty()) return m;
        m = ctx.getSlot("node.parser.output");
        if (m != null && !m.isEmpty()) return m;
        // invoke 路径：数据在 "input" slot
        Object input = ctx.getSlot("input");
        if (input instanceof Map<?, ?> map && !map.isEmpty()
                && map.values().iterator().next() instanceof MusicMetadata) {
            return (Map<?, MusicMetadata>) map;
        }
        return Map.of();
    }

    /**
     * 从 MusicMetadata 中提取目标文件路径，优先使用 Song.filePath，
     * 回退到 map key（兼容路径不变的模块，如 ChineseConvert、EncodingRepair 等）。
     */
    private static Path resolvePath(Object key, MusicMetadata meta) {
        if (meta != null && !meta.getImmutableSongs().isEmpty()) {
            String fp = meta.getImmutableSongs().get(0).getFilePath();
            if (fp != null && !fp.isBlank()) {
                return Path.of(fp);
            }
        }
        // 回退：使用 map key
        if (key instanceof Path p) return p;
        return Path.of(String.valueOf(key));
    }
}
