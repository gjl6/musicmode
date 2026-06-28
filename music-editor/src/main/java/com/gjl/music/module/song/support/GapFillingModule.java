package com.gjl.music.module.song.support;

import com.gjl.music.config.ConfigChangedEvent;
import com.gjl.music.config.ConfigService;
import com.gjl.music.exception.ModuleException;
import com.gjl.music.editor.mapper.SongManageMapper;
import com.gjl.music.infra.util.AudioFileUtils;
import com.gjl.music.model.MusicMetadata;
import com.gjl.music.module.FailurePolicy;
import com.gjl.music.module.Module;
import com.gjl.music.infra.pipeline.NodeContext;
import com.gjl.music.infra.pipeline.NodeHandler;
import com.gjl.music.infra.pipeline.NodeResult;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 按需补缺基类 —— 封装"DB 缓存 → 缺则补 → 处理 → 写回"通用流程。
 *
 * <p>使用新管道 NodeContext.invoke() 替代旧 ctx.fork() 跨模块调用。
 * 逐项处理子类覆写模板方法；集合处理子类覆写 execute 走自定义流程。
 */
@Slf4j
public abstract class GapFillingModule implements Module, NodeHandler {

    /** 可空：逐项处理子类通过 MetadataGapFillingModule 传入；集合处理子类无需 */
    protected final SongManageMapper songManageMapper;

    @Autowired
    private ConfigService configService;

    /** 热更新字段 */
    private volatile int batchSize = 200;
    private volatile int forkBatch = 50;
    private volatile int itemBatchSize = 16;
    private volatile boolean persistEnabled = true;
    private volatile boolean writeTagsEnabled = true;

    @PostConstruct
    void reloadConfig() {
        this.batchSize = configService.getInt("writer.batch_size", 200);
        this.forkBatch = configService.getInt("writer.fork_batch", 50);
        this.itemBatchSize = configService.getInt("writer.item_batch_size", 16);
        this.persistEnabled = configService.getBoolean("switches.persist_enabled", true);
        this.writeTagsEnabled = configService.getBoolean("switches.write_tags_enabled", true);
    }

    @EventListener
    public void onConfigChanged(ConfigChangedEvent e) {
        switch (e.configKey()) {
            case "writer.batch_size" -> this.batchSize = e.asInt(200);
            case "writer.fork_batch" -> this.forkBatch = e.asInt(50);
            case "writer.item_batch_size" -> this.itemBatchSize = e.asInt(16);
            case "switches.persist_enabled" -> this.persistEnabled = e.asBoolean();
            case "switches.write_tags_enabled" -> this.writeTagsEnabled = e.asBoolean();
        }
    }

    protected GapFillingModule() {
        this.songManageMapper = null;
    }

    protected GapFillingModule(SongManageMapper songManageMapper) {
        this.songManageMapper = songManageMapper;
    }

    // ── 模板方法 ──

    /** 补缺时 invoke 的目标模块名（替代旧 fillCapability） */
    protected String fillModuleName() { throw new UnsupportedOperationException(name()); }

    /** 数据处理逻辑 */
    protected MusicMetadata processItem(MusicMetadata meta) { throw new UnsupportedOperationException(name()); }

    /** 批量查询 DB 缓存 */
    protected List<Map<String, Object>> queryDbCache(List<String> paths) { throw new UnsupportedOperationException(name()); }

    /** DB 缓存行是否缺失关键数据 */
    protected boolean isDataMissing(Map<String, Object> dbRow) { throw new UnsupportedOperationException(name()); }

    /** 从 DB 缓存行重建 MusicMetadata */
    protected MusicMetadata rowToMetadata(Map<String, Object> row, String filePath) { throw new UnsupportedOperationException(name()); }

    /** 从 invoke 结果提取 MusicMetadata */
    protected MusicMetadata extractForkResult(Object forkResult, String filePath) { throw new UnsupportedOperationException(name()); }

    /** 是否写回标签文件 */
    protected boolean shouldWriteTags() { return true; }

    /** 从 options Map 中解析 boolean 值，不存在时回退到 defaultValue */
    private static boolean resolveBool(Map<String, Object> options, String key, boolean defaultValue) {
        if (options == null) return defaultValue;
        Object v = options.get(key);
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) return Boolean.parseBoolean(s);
        return defaultValue;
    }

    // ── NodeHandler 统一入口 ──

    @Override
    public NodeResult execute(NodeContext ctx) throws Exception {
        // 从上下文数据流加载模块配置
        Map<String, Object> allOptions = ctx.getSlot("options");
        if (allOptions != null) {
            Object moduleConfig = allOptions.get(name());
            if (moduleConfig instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> config = (Map<String, Object>) map;
                configure(config);
            }
        }

        // 解析持久化开关（全局默认 + per-request options 覆盖）
        boolean doPersist = resolveBool(allOptions, "persist", persistEnabled);
        boolean doWriteTags = resolveBool(allOptions, "writeTags", writeTagsEnabled) && shouldWriteTags();
        log.debug("{}: persist={}, writeTags={}", name(), doPersist, doWriteTags);

        NodeResult result = new NodeResult();
        List<Path> paths = resolveInputPaths(ctx);
        if (paths.isEmpty()) {
            log.warn("{}: 没有输入文件", name());
            return result;
        }

        log.info("{}: 开始处理 {} 个文件", name(), paths.size());

        for (int offset = 0; offset < paths.size(); offset += batchSize) {
            int end = Math.min(offset + batchSize, paths.size());
            List<Path> batch = paths.subList(offset, end);
            List<String> batchPaths = batch.stream().map(Path::toString).toList();

            // 1. 查 DB 缓存
            Map<String, Map<String, Object>> cached = buildCacheMap(batchPaths);
            log.debug("{}: 缓存命中 {} / {}", name(), cached.size(), batch.size());

            // 2. 分类
            Map<String, MusicMetadata> working = new LinkedHashMap<>();
            Map<String, Path> missingMap = new LinkedHashMap<>();

            for (Path p : batch) {
                String filePath = p.toString();
                // 非音频文件跳过，避免拖垮整批解析
                if (!AudioFileUtils.isAudioFile(p)) {
                    log.warn("{}: 跳过非音频文件 {}", name(), p.getFileName());
                    recordItemResult(ctx, result, filePath, false,
                            "不支持的文件格式: " + AudioFileUtils.extension(p));
                    continue;
                }
                Map<String, Object> row = cached.get(filePath);
                if (!isDataMissing(row)) {
                    working.put(filePath, rowToMetadata(row, filePath));
                } else {
                    missingMap.put(filePath, p);
                }
            }

            // 3. 分批 invoke 补缺（有线程池且多子批时并行，否则串行）
            Map<String, MusicMetadata> toPersist = new LinkedHashMap<>();
            if (!missingMap.isEmpty()) {
                List<Map.Entry<String, Path>> entries = new ArrayList<>(missingMap.entrySet());

                // 构建子批列表
                List<Map<String, Path>> subBatches = new ArrayList<>();
                for (int fo = 0; fo < entries.size(); fo += forkBatch) {
                    int fe = Math.min(fo + forkBatch, entries.size());
                    Map<String, Path> sub = new LinkedHashMap<>();
                    for (int i = fo; i < fe; i++) {
                        sub.put(entries.get(i).getKey(), entries.get(i).getValue());
                    }
                    subBatches.add(sub);
                }

                Executor exec = ctx.getWorkerExecutor();
                if (exec != null && subBatches.size() > 1) {
                    // ── 并行 invoke ──
                    Map<String, MusicMetadata> syncToPersist =
                            Collections.synchronizedMap(new LinkedHashMap<>());
                    Map<String, MusicMetadata> syncWorking =
                            Collections.synchronizedMap(working);
                    AtomicBoolean invokeFastFail = new AtomicBoolean(false);

                    List<CompletableFuture<Void>> invokeFutures = new ArrayList<>();
                    for (Map<String, Path> sub : subBatches) {
                        invokeFutures.add(CompletableFuture.runAsync(() -> {
                            if (invokeFastFail.get() || ctx.isCancelled()) return;
                            try {
                                Map<String, Object> invokeResult =
                                        ctx.invoke(fillModuleName(), sub);
                                synchronized (syncWorking) {
                                    int beforeSize = syncWorking.size();
                                    collectBatchResult(invokeResult, syncToPersist, syncWorking);
                                    // 记录子批中解析失败、未进入 working 的文件
                                    if (syncWorking.size() - beforeSize < sub.size()) {
                                        for (String fp : sub.keySet()) {
                                            if (!syncWorking.containsKey(fp)) {
                                                recordItemResult(ctx, result, fp, false,
                                                        "解析失败：无法获取有效元数据");
                                            }
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                log.warn("{}: 子批补缺失败，回退逐条", name());
                                for (var me : sub.entrySet()) {
                                    if (invokeFastFail.get() || ctx.isCancelled()) break;
                                    try {
                                        Map<String, Object> r = ctx.invoke(
                                                fillModuleName(), me.getValue());
                                        MusicMetadata meta = extractForkResult(r, me.getKey());
                                        if (meta != null && hasValidSong(meta)) {
                                            syncToPersist.put(me.getKey(), meta);
                                            syncWorking.put(me.getKey(), meta);
                                            recordItemResult(ctx, result, me.getKey(), true, null);
                                        } else {
                                            recordItemResult(ctx, result, me.getKey(), false,
                                                    "解析失败：无法获取有效元数据");
                                        }
                                    } catch (Exception e2) {
                                        log.warn("{}: 补缺失败 {} - {}",
                                                name(), me.getKey(), e2.getMessage());
                                        recordItemResult(ctx, result, me.getKey(), false,
                                                e2.getMessage());
                                        if (failurePolicy() == FailurePolicy.FAIL_FAST) {
                                            invokeFastFail.set(true);
                                            throw new RuntimeException(e2);
                                        }
                                    }
                                }
                            }
                        }, exec));
                    }

                    try {
                        CompletableFuture.allOf(
                                invokeFutures.toArray(new CompletableFuture[0])).join();
                    } catch (CompletionException ce) {
                        Throwable cause = ce.getCause();
                        if (failurePolicy() == FailurePolicy.FAIL_FAST
                                && cause instanceof RuntimeException re
                                && re.getCause() instanceof ModuleException me) {
                            throw me;
                        }
                        if (!(cause instanceof ModuleException)) {
                            log.warn("{}: 并行补缺部分失败: {}",
                                    name(), cause != null ? cause.getMessage() : null);
                        }
                    }

                    toPersist.putAll(syncToPersist);
                } else {
                    // ── 串行 invoke（原逻辑，executor 为空或只有 1 个子批时使用） ──
                    for (Map<String, Path> sub : subBatches) {
                        try {
                            Map<String, Object> invokeResult =
                                    ctx.invoke(fillModuleName(), sub);
                            int beforeSize = working.size();
                            collectBatchResult(invokeResult, toPersist, working);
                            if (working.size() - beforeSize < sub.size()) {
                                for (String fp : sub.keySet()) {
                                    if (!working.containsKey(fp)) {
                                        recordItemResult(ctx, result, fp, false,
                                                "解析失败：无法获取有效元数据");
                                    }
                                }
                            }
                        } catch (Exception e) {
                            log.warn("{}: 子批补缺失败，回退逐条", name());
                            for (var me : sub.entrySet()) {
                                try {
                                    Map<String, Object> r = ctx.invoke(
                                            fillModuleName(), me.getValue());
                                    MusicMetadata meta = extractForkResult(r, me.getKey());
                                    if (meta != null && hasValidSong(meta)) {
                                        toPersist.put(me.getKey(), meta);
                                        working.put(me.getKey(), meta);
                                        recordItemResult(ctx, result, me.getKey(), true, null);
                                    } else {
                                        recordItemResult(ctx, result, me.getKey(), false,
                                                "解析失败：无法获取有效元数据");
                                    }
                                } catch (Exception e2) {
                                    log.warn("{}: 补缺失败 {} - {}",
                                            name(), me.getKey(), e2.getMessage());
                                    recordItemResult(ctx, result, me.getKey(), false,
                                            e2.getMessage());
                                }
                            }
                        }
                    }
                }
            }

            if (!toPersist.isEmpty() && doPersist) {
                try {
                    ctx.invoke("db-operator", toPersist);
                    log.debug("{}: 回填 {} 条到 DB", name(), toPersist.size());
                } catch (Exception e) {
                    log.warn("{}: DB 回填失败: {}", name(), e.getMessage());
                }
            }

            // 4. 逐项处理（有 executor 且多项时并行）
            log.info("{}: working={}, missing={}", name(), working.size(), missingMap.size());
            Executor exec = ctx.getWorkerExecutor();
            Map<String, MusicMetadata> processed;
            if (exec != null && working.size() > 1) {
                processed = Collections.synchronizedMap(new LinkedHashMap<>());
                AtomicBoolean fastFail = new AtomicBoolean(false);
                List<Map.Entry<String, MusicMetadata>> entries =
                        new ArrayList<>(working.entrySet());

                // 打印每个文件的搜索关键词，方便排查数据串扰
                if (log.isDebugEnabled()) {
                    for (var e : entries) {
                        var meta = e.getValue();
                        var songs = meta.getImmutableSongs();
                        var artists = meta.getImmutableArtists();
                        String t = songs.isEmpty() ? "?" : songs.getFirst().getTitle();
                        String a = artists.isEmpty() ? "" : artists.getFirst().getArtistName();
                        log.debug("{}: 待处理 file=[{}] title=[{}] artist=[{}]",
                                name(), e.getKey(), t, a);
                    }
                }

                List<CompletableFuture<Void>> futures = new ArrayList<>();
                for (int i = 0; i < entries.size(); i += itemBatchSize) {
                    int batchEnd = Math.min(i + itemBatchSize, entries.size());
                    List<Map.Entry<String, MusicMetadata>> itemBatch =
                            entries.subList(i, batchEnd);
                    futures.add(CompletableFuture.runAsync(() -> {
                        for (var e : itemBatch) {
                            if (fastFail.get() || ctx.isCancelled()) break;
                            if (ctx.isPausing()) {
                                try {
                                    ctx.checkPause();
                                } catch (NodeContext.CancelledException ce) {
                                    break;
                                }
                            }
                            String itemKey = e.getKey();
                            try {
                                MusicMetadata itemResult = processItem(e.getValue());
                                if (itemResult != null) {
                                    processed.put(itemKey, itemResult);
                                    recordItemResult(ctx, result, itemKey, true, null);
                                } else {
                                    recordItemResult(ctx, result, itemKey, true, "skipped");
                                }
                            } catch (SkipException se) {
                                // 跳过是正常的模块行为（如已是目标格式），记录为成功
                                recordItemResult(ctx, result, itemKey, true, "skipped: " + se.getMessage());
                            } catch (ModuleException me) {
                                log.warn("{}: 处理失败 {} - {}", name(), itemKey, me.getMessage());
                                recordItemResult(ctx, result, itemKey, false, me.getMessage());
                                if (failurePolicy() == FailurePolicy.FAIL_FAST) {
                                    fastFail.set(true);
                                    throw new RuntimeException(me);
                                }
                            } catch (Exception ex) {
                                log.warn("{}: 处理失败 {} - {}", name(), itemKey, ex.getMessage());
                                recordItemResult(ctx, result, itemKey, false, ex.getMessage());
                                if (failurePolicy() == FailurePolicy.FAIL_FAST) {
                                    fastFail.set(true);
                                    throw new RuntimeException(
                                            new ModuleException(name(), itemKey, ex.getMessage(), ex));
                                }
                            }
                        }
                    }, exec));
                }

                try {
                    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                } catch (CompletionException ce) {
                    Throwable cause = ce.getCause();
                    if (failurePolicy() == FailurePolicy.FAIL_FAST) {
                        if (cause instanceof RuntimeException re
                                && re.getCause() instanceof ModuleException me) {
                            throw me;
                        }
                        throw new ModuleException(name(), "batch failed",
                                cause != null ? cause.getMessage() : null,
                                cause instanceof Exception ? (Exception) cause : null);
                    }
                }
            } else {
                processed = new LinkedHashMap<>();
                for (var entry : working.entrySet()) {
                    if (ctx.isCancelled()) break;
                    try {
                        ctx.checkPause();
                    } catch (NodeContext.CancelledException ce) {
                        break;
                    }
                    String itemKey = entry.getKey();
                    try {
                        MusicMetadata itemResult = processItem(entry.getValue());
                        if (itemResult != null) {
                            processed.put(itemKey, itemResult);
                            recordItemResult(ctx, result, itemKey, true, null);
                        } else {
                            recordItemResult(ctx, result, itemKey, true, "skipped");
                        }
                    } catch (SkipException e) {
                        // 跳过是正常的模块行为（如已是目标格式），记录为成功
                        recordItemResult(ctx, result, itemKey, true, "skipped: " + e.getMessage());
                    } catch (ModuleException e) {
                        log.warn("{}: 处理失败 {} - {}", name(), itemKey, e.getMessage());
                        recordItemResult(ctx, result, itemKey, false, e.getMessage());
                        if (failurePolicy() == FailurePolicy.FAIL_FAST) throw e;
                    } catch (Exception e) {
                        log.warn("{}: 处理失败 {} - {}", name(), itemKey, e.getMessage());
                        recordItemResult(ctx, result, itemKey, false, e.getMessage());
                        if (failurePolicy() == FailurePolicy.FAIL_FAST)
                            throw new ModuleException(name(), itemKey, e.getMessage(), e);
                    }
                }
            }

            // 5. 写回（DB + 文件），受配置开关控制
            if (!processed.isEmpty()) {
                // DB 持久化：单线程（persistBatch 整批一次提交）
                if (doPersist) {
                    try {
                        ctx.invoke("db-operator", processed);
                    } catch (Exception e) {
                        log.warn("{}: DB 回填失败: {}", name(), e.getMessage());
                    }
                }
                // 文件标签回写：有 executor 且多项时多线程并行
                if (doWriteTags) {
                    List<Map.Entry<String, MusicMetadata>> writeEntries =
                            new ArrayList<>(processed.entrySet());
                    if (exec != null && writeEntries.size() > 1) {
                        List<CompletableFuture<Void>> writeFutures = new ArrayList<>();
                        for (int wi = 0; wi < writeEntries.size(); wi += itemBatchSize) {
                            int we = Math.min(wi + itemBatchSize, writeEntries.size());
                            Map<String, MusicMetadata> subBatch = new LinkedHashMap<>();
                            for (int j = wi; j < we; j++) {
                                var e = writeEntries.get(j);
                                subBatch.put(e.getKey(), e.getValue());
                            }
                            writeFutures.add(CompletableFuture.runAsync(() -> {
                                try {
                                    ctx.invoke("writer", subBatch);
                                } catch (Exception ex) {
                                    log.warn("{}: 标签回写子批失败: {}", name(), ex.getMessage());
                                }
                            }, exec));
                        }
                        try {
                            CompletableFuture.allOf(
                                    writeFutures.toArray(new CompletableFuture[0])).join();
                        } catch (CompletionException ce) {
                            log.warn("{}: 并行标签回写部分失败: {}", name(),
                                    ce.getCause() != null ? ce.getCause().getMessage() : null);
                        }
                    } else {
                        try {
                            ctx.invoke("writer", processed);
                        } catch (Exception e) {
                            log.warn("{}: 标签回写失败: {}", name(), e.getMessage());
                        }
                    }
                }
            }
        }

        log.info("{}: 处理完成, success={}, failed={}",
                name(), result.successCount(), result.failedCount());
        return result;
    }

    // ── 辅助 ──

    private Map<String, Map<String, Object>> buildCacheMap(List<String> paths) {
        Map<String, Map<String, Object>> map = new LinkedHashMap<>();
        for (Map<String, Object> row : queryDbCache(paths)) {
            String fp = extractFilePath(row);
            if (fp != null) map.put(fp, row);
        }
        return map;
    }

    protected String extractFilePath(Map<String, Object> row) {
        Object v = row.get("FILEPATH");
        if (v == null) v = row.get("filePath");
        if (v == null) v = row.get("FILE_PATH");
        if (v == null) v = row.get("file_path");
        return v != null ? v.toString() : null;
    }

    protected boolean hasValidSong(MusicMetadata meta) {
        return !meta.getImmutableSongs().isEmpty();
    }

    /**
     * 从 invoke 返回的 outputs Map 中收集 MusicMetadata。
     * invoke 返回格式为 {@code {"node.parser.output": Map<Path, MusicMetadata>}}，
     * 需要展开内层 Map 再逐条提取。
     */
    @SuppressWarnings("unchecked")
    private void collectBatchResult(Object result, Map<String, MusicMetadata> toPersist,
                                     Map<String, MusicMetadata> working) {
        if (!(result instanceof Map<?,?> outputs)) return;
        // 遍历 outputs 中的每个 value：可能是 Map<Path, MusicMetadata> 或直接的 MusicMetadata
        for (var entry : outputs.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map<?,?> innerMap) {
                // 内层 Map：key=Path/String, value=MusicMetadata
                for (var inner : innerMap.entrySet()) {
                    String filePath = inner.getKey().toString();
                    if (inner.getValue() instanceof MusicMetadata meta && hasValidSong(meta)) {
                        toPersist.put(filePath, meta);
                        working.put(filePath, meta);
                    }
                }
            } else if (value instanceof MusicMetadata meta && hasValidSong(meta)) {
                // 直接 MusicMetadata（使用 entry key 作为 filePath）
                toPersist.put(entry.getKey().toString(), meta);
                working.put(entry.getKey().toString(), meta);
            }
        }
    }

    /** 同时记录到 NodeResult 并通过 NodeContext 实时上报进度 */
    private void recordItemResult(NodeContext ctx, NodeResult result,
                                   String itemKey, boolean success, String errorMessage) {
        result.addItemResult(itemKey, success, errorMessage);
        ctx.reportItemComplete(itemKey, success, errorMessage);
    }

    /** 从 context 获取输入路径，兼容拓扑节点、初始路径和 invoke 调用三种模式 */
    @SuppressWarnings("unchecked")
    protected List<Path> resolveInputPaths(NodeContext ctx) {
        // 拓扑节点路径：scanner 模块写入的共享 slot
        Object scannerOut = ctx.getSlot("node.scanner.output");
        if (scannerOut instanceof List<?> list && !list.isEmpty() && list.getFirst() instanceof Path) {
            return (List<Path>) list;
        }
        // 初始输入路径：委托 scanner 模块统一扫描（不自行 walk 目录）
        Path[] inputPaths = ctx.getSlot("input.paths");
        if (inputPaths != null && inputPaths.length > 0) {
            List<Path> result = new ArrayList<>();
            for (Path p : inputPaths) {
                try {
                    if (Files.isDirectory(p)) {
                        // 委托 scanner 模块收集音频文件
                        Map<String, Object> scanOut = ctx.invoke("scanner", new Path[]{p});
                        Object output = scanOut.get("node.scanner.output");
                        if (output instanceof List<?> list) {
                            for (Object item : list) {
                                if (item instanceof Path audioFile) {
                                    result.add(audioFile);
                                }
                            }
                        }
                    } else if (AudioFileUtils.isAudioFile(p)) {
                        result.add(p);
                    }
                } catch (Exception e) {
                    log.warn("{}: 委托 scanner 扫描失败 {}，跳过: {}",
                            name(), p, e.getMessage());
                }
            }
            return result;
        }
        // invoke 路径：通过 ctx.invoke("moduleName", Map<String, Path>) 传入
        Object invokeInput = ctx.getSlot("input");
        if (invokeInput instanceof Map<?, ?> map && !map.isEmpty()) {
            Object firstValue = map.values().iterator().next();
            if (firstValue instanceof Path) {
                return map.values().stream()
                        .filter(Path.class::isInstance)
                        .map(Path.class::cast)
                        .toList();
            }
        }
        return List.of();
    }
}
