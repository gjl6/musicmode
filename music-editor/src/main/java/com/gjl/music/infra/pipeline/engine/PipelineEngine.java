package com.gjl.music.infra.pipeline.engine;

import com.gjl.music.module.Module;
import com.gjl.music.infra.pipeline.*;
import com.gjl.music.infra.pipeline.graph.GraphNode;
import com.gjl.music.infra.pipeline.graph.PipelineGraph;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * 核心执行引擎 —— 按深度分组调度节点，同深度并行、跨深度串行。
 *
 * <h3>执行流程</h3>
 * <pre>
 * for each DepthGroup (ordered by depth):
 *     检查暂停/取消
 *     组内所有节点并行提交到 workerExecutor
 *     等待全组 CompletableFuture.allOf().join()
 *     收集结果 → 合并 outputs 到 context slots
 *     最终节点完成后 → 逐文件写日志 + 立即更新进度
 * </pre>
 *
 * <h3>Handler 注册表</h3>
 * 引擎维护 {@code Map<String, NodeHandler>}，NodeContext.invoke() 通过它查找并同步调用。
 * 被 invoke 的模块不参与拓扑深度分组，作为"旁路服务"存在。
 */
@Slf4j
public class PipelineEngine {

    private final String pipelineId;
    private final PipelineGraph graph;
    private final Map<String, NodeHandler> handlerRegistry;
    private final FileProgressTracker progressTracker;
    private final PauseController pauseController;
    private final Executor workerExecutor;
    private final Executor virtualExecutor;
    private final ItemLogWriter logWriter;
    private final Consumer<NodeContext> contextInitializer;
    private volatile PipelineState currentState = PipelineState.READY;

    /**
     * 文件级日志写入回调 —— 由 Engine 在最终节点完成后调用。
     */
    @FunctionalInterface
    public interface ItemLogWriter {
        /**
         * @param pipelineId 管道 ID
         * @param itemKey    文件标识
         * @param success    是否成功
         * @param errorNode  失败节点名（成功时为 null）
         * @param errorMsg   错误信息
         */
        void write(String pipelineId, String itemKey, boolean success, String errorNode, String errorMsg);
    }

    public PipelineEngine(String pipelineId,
                          PipelineGraph graph,
                          Map<String, NodeHandler> handlerRegistry,
                          FileProgressTracker progressTracker,
                          PauseController pauseController,
                          Executor workerExecutor,
                          Executor virtualExecutor,
                          ItemLogWriter logWriter,
                          Consumer<NodeContext> contextInitializer) {
        this.pipelineId = pipelineId;
        this.graph = graph;
        this.handlerRegistry = new ConcurrentHashMap<>(handlerRegistry);
        this.progressTracker = progressTracker;
        this.pauseController = pauseController;
        this.workerExecutor = workerExecutor;
        this.virtualExecutor = virtualExecutor;
        this.logWriter = logWriter;
        this.contextInitializer = contextInitializer;
    }

    // ── 状态管理 ──

    public String getPipelineId() { return pipelineId; }
    public PipelineState getCurrentState() { return currentState; }
    public void setCurrentState(PipelineState state) { this.currentState = state; }
    public FileProgressTracker getProgressTracker() { return progressTracker; }
    public PauseController getPauseController() { return pauseController; }

    /**
     * 执行管道，阻塞直到完成或失败/取消。
     */
    public PipelineResult execute() {
        long startMs = System.currentTimeMillis();
        this.currentState = PipelineState.RUNNING;
        List<List<GraphNode>> depthGroups = graph.getDepthGroups();
        ContextImpl ctx = new ContextImpl(handlerRegistry, pauseController,
                workerExecutor, virtualExecutor);
        if (contextInitializer != null) {
            contextInitializer.accept(ctx);
        }

        try {
            for (int depth = 0; depth < depthGroups.size(); depth++) {
                List<GraphNode> group = depthGroups.get(depth);

                // 跳过空组
                if (group.isEmpty()) continue;

                // 暂停检测
                pauseController.checkPause();

                log.debug("[{}] Executing depth group {}: {}",
                        pipelineId, depth,
                        group.stream().map(GraphNode::getId).toList());

                // 组内并行，根据 handler executorType 选择线程池
                List<CompletableFuture<NodeResult>> futures = new ArrayList<>();
                for (GraphNode node : group) {
                    NodeHandler handler = resolveHandler(node);
                    Executor exec = resolveExecutor(handler);
                    futures.add(CompletableFuture.supplyAsync(() -> {
                        ContextImpl.CTX_EXECUTOR.set(exec);
                        try {
                            return handler.execute(ctx);
                        } catch (NodeContext.CancelledException e) {
                            throw e;
                        } catch (Exception e) {
                            log.error("[{}] Node [{}] execution error", pipelineId, node.getId(), e);
                            throw new CompletionException(e);
                        } finally {
                            ContextImpl.CTX_EXECUTOR.remove();
                        }
                    }, exec));
                }

                // 等待全组完成
                try {
                    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                } catch (CompletionException e) {
                    // 检查是否是取消
                    if (e.getCause() instanceof NodeContext.CancelledException) {
                        throw (NodeContext.CancelledException) e.getCause();
                    }
                    // 非最终节点失败 → 继续（不影响其他同深度节点）
                    // 最终节点失败 → 在下面处理
                }

                // 收集结果 + 合并 outputs + 写日志
                for (int i = 0; i < group.size(); i++) {
                    GraphNode node = group.get(i);
                    NodeResult result;
                    try {
                        result = futures.get(i).get();
                    } catch (ExecutionException e) {
                        // 节点执行失败
                        if (node.isFinalNode()) {
                            progressTracker.setErrorNode(node.getId());
                            progressTracker.setErrorMessage(e.getCause().getMessage());
                            // 已处理的部分仍然写日志
                            flushItemLogs(ctx);
                            long duration = System.currentTimeMillis() - startMs - pauseController.getTotalPausedMs();
                            this.currentState = PipelineState.FAILED;
                            log.error("[{}] Final node [{}] failed", pipelineId, node.getId(), e.getCause());
                            return new PipelineResult(pipelineId, PipelineState.FAILED,
                                    progressTracker.getTotalFiles(),
                                    progressTracker.getSuccessFiles(),
                                    progressTracker.getFailedFiles(),
                                    node.getId(), e.getCause().getMessage(), duration);
                        }
                        // 非最终节点失败，记录但不中断（引擎继续到最终节点）
                        log.warn("[{}] Non-final node [{}] failed, continuing", pipelineId, node.getId());
                        continue;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        this.currentState = PipelineState.CANCELLED;
                        long duration = System.currentTimeMillis() - startMs - pauseController.getTotalPausedMs();
                        return new PipelineResult(pipelineId, PipelineState.CANCELLED,
                                progressTracker.getTotalFiles(),
                                progressTracker.getSuccessFiles(),
                                progressTracker.getFailedFiles(),
                                null, "Interrupted", duration);
                    }

                    if (result == null) continue;

                    // 合并 outputs 到 context slots
                    result.getOutputs().forEach(ctx::setSlot);

                    // scanner / artist-scanner 完成后立即设置 totalFiles（前端实时进度需要）
                    if (!node.isFinalNode()) {
                        Object scannerOutput = result.getOutputs().get("node.scanner.output");
                        if (scannerOutput == null) {
                            scannerOutput = result.getOutputs().get("node.artist-scanner.output");
                        }
                        if (scannerOutput instanceof List<?> items) {
                            progressTracker.setTotalFiles(items.size());
                        }
                    }

                    // 最终节点 → 写日志 + 更新进度
                    if (node.isFinalNode()) {
                        // 二次安全保障：如果 scanner 未设置 totalFiles（如 delete 模板没有 scanner），此处兜底
                        if (progressTracker.getTotalFiles() == 0) {
                            progressTracker.setTotalFiles(result.getItemResults().size());
                        }
                        // 防御性对账：如果 scanner 设置了 totalFiles 但最终节点产出的
                        // ItemResult 数量更少，以实际数为准并告警（表明有文件在管道中丢失）。
                        // 注意：不处理 itemResultCount > scannerTotal 的情况（cue-split
                        // 等模块会从单个文件产生多个输出，这是正常行为）。
                        int scannerTotal = progressTracker.getTotalFiles();
                        int itemResultCount = result.getItemResults().size();
                        if (scannerTotal > 0 && itemResultCount < scannerTotal) {
                            log.warn("[{}] Scanner reported {} files but final node [{}] produced only {} ItemResults. "
                                    + "{} file(s) were lost in the pipeline. Adjusting totalFiles to match.",
                                    pipelineId, scannerTotal, node.getId(), itemResultCount,
                                    scannerTotal - itemResultCount);
                            progressTracker.setTotalFiles(itemResultCount);
                        }
                        String errorNode = null;
                        for (NodeResult.ItemResult item : result.getItemResults()) {
                            if (!item.success()) {
                                errorNode = node.getId();
                                progressTracker.setErrorNode(node.getId());
                            }
                            // 模块已通过 reportItemComplete 实时上报的，跳过避免重复计数
                            if (ctx.reportedItems.contains(item.itemKey())) {
                                continue;
                            }
                            progressTracker.markFileComplete(item.itemKey(), item.success(), item.errorMessage());
                            logWriter.write(pipelineId, item.itemKey(), item.success(),
                                    item.success() ? null : node.getId(), item.errorMessage());
                        }
                    }
                }
            }

            // 所有深度组执行完毕
            flushItemLogs(ctx);
            long duration = System.currentTimeMillis() - startMs - pauseController.getTotalPausedMs();
            this.currentState = PipelineState.COMPLETED;
            log.info("[{}] Pipeline completed: {} success, {} failed in {}ms",
                    pipelineId, progressTracker.getSuccessFiles(),
                    progressTracker.getFailedFiles(), duration);

            return new PipelineResult(pipelineId, PipelineState.COMPLETED,
                    progressTracker.getTotalFiles(),
                    progressTracker.getSuccessFiles(),
                    progressTracker.getFailedFiles(),
                    null, null, duration);

        } catch (NodeContext.CancelledException e) {
            flushItemLogs(ctx);
            long duration = System.currentTimeMillis() - startMs - pauseController.getTotalPausedMs();
            this.currentState = PipelineState.CANCELLED;
            log.info("[{}] Pipeline cancelled", pipelineId);
            return new PipelineResult(pipelineId, PipelineState.CANCELLED,
                    progressTracker.getTotalFiles(),
                    progressTracker.getSuccessFiles(),
                    progressTracker.getFailedFiles(),
                    null, "Cancelled", duration);
        }
    }

    /** 根据 handler 的 executorType 选择线程池 */
    private Executor resolveExecutor(NodeHandler handler) {
        if (handler instanceof Module m && m.executorType() == Module.ExecutorType.VIRTUAL) {
            return virtualExecutor;
        }
        return workerExecutor;
    }

    /** 解析节点的 handler：优先用 node 预设 handler，否则从 registry 按 moduleName 查找 */
    private NodeHandler resolveHandler(GraphNode node) {
        if (node.getHandler() != null) {
            return node.getHandler();
        }
        NodeHandler handler = handlerRegistry.get(node.getModuleName());
        if (handler == null) {
            handler = handlerRegistry.get(node.getId());
        }
        if (handler == null) {
            throw new IllegalStateException(
                    "No handler found for node [" + node.getId()
                    + "], moduleName=" + node.getModuleName()
                    + ". Registered handlers: " + handlerRegistry.keySet());
        }
        return handler;
    }

    private void flushItemLogs(ContextImpl ctx) {
        // persistence flush 由外部 orchestrator 管理
    }

    // ═══════════════════════════════════════════════════════════════
    // NodeContext 实现
    // ═══════════════════════════════════════════════════════════════

    private class ContextImpl implements NodeContext {

        /** 当前线程执行节点所用的 Executor（由 PipelineEngine 在 supplyAsync 前设置） */
        static final ThreadLocal<Executor> CTX_EXECUTOR = new ThreadLocal<>();

        private final String pipelineId = PipelineEngine.this.pipelineId;
        private final ConcurrentHashMap<String, Object> slots = new ConcurrentHashMap<>();
        private final Map<String, NodeHandler> handlerRegistry;
        private final PauseController pauseController;
        private final Executor workerExecutor;
        private final Executor virtualExecutor;

        /** 已通过 reportItemComplete 实时上报的文件，防止最终节点兜底循环重复计数 */
        private final Set<String> reportedItems = ConcurrentHashMap.newKeySet();

        ContextImpl(Map<String, NodeHandler> handlerRegistry,
                    PauseController pauseController,
                    Executor workerExecutor,
                    Executor virtualExecutor) {
            this.handlerRegistry = handlerRegistry;
            this.pauseController = pauseController;
            this.workerExecutor = workerExecutor;
            this.virtualExecutor = virtualExecutor;
        }

        /** 根据 handler 的 executorType 解析应使用的线程池 */
        private Executor resolveExecutor(NodeHandler handler) {
            if (handler instanceof Module m && m.executorType() == Module.ExecutorType.VIRTUAL) {
                return virtualExecutor;
            }
            return workerExecutor;
        }

        @Override
        public String getPipelineId() {
            return pipelineId;
        }

        @Override
        public Map<String, Object> slots() {
            return slots;
        }

        @Override
        public void reportItemComplete(String itemKey, boolean success, String errorMessage) {
            if (reportedItems.add(itemKey)) {
                progressTracker.markFileComplete(itemKey, success, errorMessage);
                // 实时上报不设具体 errorNode（兜底循环会补充），先保证计数器实时推进
                logWriter.write(pipelineId, itemKey, success, null, errorMessage);
            }
        }

        @Override
        public Map<String, Object> invoke(String moduleName, Object input) throws Exception {
            NodeHandler handler = handlerRegistry.get(moduleName);
            if (handler == null) {
                throw new IllegalArgumentException("No handler registered for module: " + moduleName);
            }

            // 为被调用模块创建隔离的临时 context，根据 callee 的 executorType 选择线程池
            Executor calleeExec = resolveExecutor(handler);
            ContextImpl calleeCtx = new ContextImpl(handlerRegistry, pauseController,
                    workerExecutor, virtualExecutor);
            calleeCtx.setSlot("input", input);

            // 设置 callee 线程池，以便 callee 内部 getWorkerExecutor() 返回正确的 executor
            Executor previous = CTX_EXECUTOR.get();
            CTX_EXECUTOR.set(calleeExec);
            try {
                NodeResult result = handler.execute(calleeCtx);
                return result != null ? result.getOutputs() : Collections.emptyMap();
            } finally {
                CTX_EXECUTOR.set(previous);
            }
        }

        @Override
        public boolean isCancelled() {
            return pauseController.isCancelled();
        }

        @Override
        public boolean isPausing() {
            return pauseController.isPausing() || pauseController.isPaused();
        }

        @Override
        public void checkPause() throws CancelledException {
            pauseController.checkPause();
        }

        @Override
        public Executor getWorkerExecutor() {
            Executor exec = CTX_EXECUTOR.get();
            return exec != null ? exec : workerExecutor;
        }

    }
}
