package com.gjl.music.pipeline.engine;

import com.gjl.music.module.Module;
import com.gjl.music.pipeline.*;
import com.gjl.music.pipeline.graph.GraphNode;
import com.gjl.music.pipeline.graph.PipelineGraph;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;


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


    @FunctionalInterface
    public interface ItemLogWriter {


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


    public String getPipelineId() { return pipelineId; }
    public PipelineState getCurrentState() { return currentState; }
    public void setCurrentState(PipelineState state) { this.currentState = state; }
    public FileProgressTracker getProgressTracker() { return progressTracker; }
    public PauseController getPauseController() { return pauseController; }


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

                                if (group.isEmpty()) continue;

                                pauseController.checkPause();

                log.debug("[{}] Executing depth group {}: {}",
                        pipelineId, depth,
                        group.stream().map(GraphNode::getId).toList());

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

                                try {
                    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                } catch (CompletionException e) {
                                        if (e.getCause() instanceof NodeContext.CancelledException) {
                        throw (NodeContext.CancelledException) e.getCause();
                    }
                                                        }

                                for (int i = 0; i < group.size(); i++) {
                    GraphNode node = group.get(i);
                    NodeResult result;
                    try {
                        result = futures.get(i).get();
                    } catch (ExecutionException e) {
                                                if (node.isFinalNode()) {
                            progressTracker.setErrorNode(node.getId());
                            progressTracker.setErrorMessage(e.getCause().getMessage());
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

                                        result.getOutputs().forEach(ctx::setSlot);

                                        if (!node.isFinalNode()) {
                        Object scannerOutput = result.getOutputs().get("node.scanner.output");
                        if (scannerOutput == null) {
                            scannerOutput = result.getOutputs().get("node.artist-scanner.output");
                        }
                        if (scannerOutput instanceof List<?> items) {
                            progressTracker.setTotalFiles(items.size());
                        }
                    }

                                        if (node.isFinalNode()) {
                                                if (progressTracker.getTotalFiles() == 0) {
                            progressTracker.setTotalFiles(result.getItemResults().size());
                        }
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


    private Executor resolveExecutor(NodeHandler handler) {
        if (handler instanceof Module m && m.executorType() == Module.ExecutorType.VIRTUAL) {
            return virtualExecutor;
        }
        return workerExecutor;
    }


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
            }


    private class ContextImpl implements NodeContext {


        static final ThreadLocal<Executor> CTX_EXECUTOR = new ThreadLocal<>();

        private final String pipelineId = PipelineEngine.this.pipelineId;
        private final ConcurrentHashMap<String, Object> slots = new ConcurrentHashMap<>();
        private final Map<String, NodeHandler> handlerRegistry;
        private final PauseController pauseController;
        private final Executor workerExecutor;
        private final Executor virtualExecutor;


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
                                logWriter.write(pipelineId, itemKey, success, null, errorMessage);
            }
        }

        @Override
        public Map<String, Object> invoke(String moduleName, Object input) throws Exception {
            NodeHandler handler = handlerRegistry.get(moduleName);
            if (handler == null) {
                throw new IllegalArgumentException("No handler registered for module: " + moduleName);
            }

                        Executor calleeExec = resolveExecutor(handler);
            ContextImpl calleeCtx = new ContextImpl(handlerRegistry, pauseController,
                    workerExecutor, virtualExecutor);
            calleeCtx.setSlot("input", input);

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
