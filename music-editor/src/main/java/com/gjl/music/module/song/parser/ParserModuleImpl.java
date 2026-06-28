package com.gjl.music.module.song.parser;

import com.gjl.music.exception.MetadataParseException;
import com.gjl.music.exception.ModuleException;
import com.gjl.music.model.MusicMetadata;
import com.gjl.music.module.FailurePolicy;
import com.gjl.music.parser.ParserFactory;
import com.gjl.music.infra.pipeline.NodeContext;
import com.gjl.music.infra.pipeline.NodeHandler;
import com.gjl.music.infra.pipeline.NodeResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class ParserModuleImpl implements ParserModule, NodeHandler {

    private final ParserFactory parserFactory;

    public ParserModuleImpl(ParserFactory parserFactory) {
        this.parserFactory = parserFactory;
    }

    @Override
    public ParserFactory getParserFactory() {
        return parserFactory;
    }

    @Override public String name() { return "parser"; }
    @Override public FailurePolicy failurePolicy() { return FailurePolicy.SKIP; }
    @Override public String label() { return "标签解析"; }

    // ── 流式模式 ──

    public Object process(Object input) {
        Path path = (Path) input;
        try {
            MusicMetadata metadata = parserFactory.parse(path.toFile());
            return Map.entry(path, metadata);
        } catch (MetadataParseException e) {
            throw new ModuleException(name(), path.toString(),
                    "解析失败: " + path.getFileName(), e);
        }
    }

    // ── 批量模式 ──

    @SuppressWarnings("unchecked")
    @Override
    public NodeResult execute(NodeContext ctx) throws Exception {
        NodeResult result = new NodeResult();
        List<Path> files = resolveInput(ctx);
        if (files == null || files.isEmpty()) {
            log.warn("没有找到任何待解析的音频文件");
            ctx.setSlot("node.parser.output", Map.of());
            return result;
        }

        log.info("开始解析 {} 个音频文件", files.size());
        Map<Path, MusicMetadata> parsedMap = new LinkedHashMap<>();

        for (Path path : files) {
            try {
                MusicMetadata metadata = parserFactory.parse(path.toFile());
                parsedMap.put(path, metadata);
                result.addItemResult(path.toString(), true, null);
            } catch (MetadataParseException e) {
                log.warn("解析失败(跳过): {} - {}", path.getFileName(), e.getMessage());
                result.addItemResult(path.toString(), false, e.getMessage());
                // SKIP: 单个文件失败不拖垮整批，继续处理后续文件
            }
        }

        ctx.setSlot("node.parser.output", parsedMap);
        result.addOutput("node.parser.output", parsedMap);
        log.info("解析完成，共解析 {} 个文件", parsedMap.size());
        return result;
    }

    /**
     * 解析输入文件列表，兼容两种路径：
     * <ul>
     *   <li>拓扑节点：从 {@code node.scanner.output} 读取 {@code List<Path>}</li>
     *   <li>invoke 调用：从 {@code input} 读取 {@code Map<String, Path>}（值集合即文件列表）</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    private List<Path> resolveInput(NodeContext ctx) {
        // invoke 路径：GapFillingModule 通过 ctx.invoke("parser", missingMap) 传入 Map<String, Path>
        Object input = ctx.getSlot("input");
        if (input instanceof Map<?, ?> map && !map.isEmpty()) {
            Object firstValue = map.values().iterator().next();
            if (firstValue instanceof Path) {
                return map.values().stream()
                        .filter(Path.class::isInstance)
                        .map(Path.class::cast)
                        .toList();
            }
        }
        if (input instanceof List<?> list && !list.isEmpty() && list.getFirst() instanceof Path) {
            return (List<Path>) list;
        }
        // 回退逐条路径：GapFillingModule 回退时传单个 Path 对象
        if (input instanceof Path path) {
            return List.of(path);
        }
        // 拓扑节点路径：scanner 模块写入的共享 slot
        List<Path> files = ctx.getSlot("node.scanner.output");
        return files != null ? files : List.of();
    }
}
