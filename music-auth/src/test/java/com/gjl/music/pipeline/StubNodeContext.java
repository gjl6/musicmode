package com.gjl.music.pipeline;

import java.util.*;
import java.util.concurrent.*;


public class StubNodeContext implements NodeContext {

    private final ConcurrentHashMap<String, Object> slots = new ConcurrentHashMap<>();
    private final Map<String, NodeHandler> handlerRegistry;
    private final Executor workerExecutor;
    private final String pipelineId;
    private volatile boolean cancelled;
    private volatile boolean paused;

    public StubNodeContext() {
        this.handlerRegistry = new LinkedHashMap<>();
        this.workerExecutor = Runnable::run;
        this.pipelineId = "stub-pipeline";
    }

    public StubNodeContext(Map<String, NodeHandler> handlerRegistry) {
        this.handlerRegistry = new LinkedHashMap<>(handlerRegistry);
        this.workerExecutor = Runnable::run;
        this.pipelineId = "stub-pipeline";
    }

    public StubNodeContext(Map<String, NodeHandler> handlerRegistry, Executor workerExecutor) {
        this.handlerRegistry = new LinkedHashMap<>(handlerRegistry);
        this.workerExecutor = workerExecutor;
        this.pipelineId = "stub-pipeline";
    }


    @Override
    public String getPipelineId() {
        return pipelineId;
    }


    @Override
    @SuppressWarnings("unchecked")
    public <T> T getSlot(String key) {
        return (T) slots.get(key);
    }

    @Override
    public void setSlot(String key, Object value) {
        slots.put(key, value);
    }

    @Override
    public Map<String, Object> slots() {
        return slots;
    }


    @Override
    public Map<String, Object> invoke(String moduleName, Object input) throws Exception {
        NodeHandler handler = handlerRegistry.get(moduleName);
        if (handler == null) {
            throw new IllegalArgumentException("No handler registered for: " + moduleName);
        }
                StubNodeContext calleeCtx = new StubNodeContext(handlerRegistry, workerExecutor);
        calleeCtx.setSlot("input", input);
        NodeResult result = handler.execute(calleeCtx);
        return result != null ? result.getOutputs() : Collections.emptyMap();
    }


    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public boolean isPausing() {
        return paused;
    }

    @Override
    public void checkPause() throws CancelledException {
        if (cancelled) throw new CancelledException();
    }

    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    public boolean isPaused() { return paused; }
    public void setPaused(boolean paused) { this.paused = paused; }


    @Override
    public Executor getWorkerExecutor() {
        return workerExecutor;
    }


    public static StubNodeContext withSlots(String k1, Object v1) {
        StubNodeContext ctx = new StubNodeContext();
        ctx.setSlot(k1, v1);
        return ctx;
    }

    public static StubNodeContext withSlots(String k1, Object v1, String k2, Object v2) {
        StubNodeContext ctx = new StubNodeContext();
        ctx.setSlot(k1, v1);
        ctx.setSlot(k2, v2);
        return ctx;
    }
}
