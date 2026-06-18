package com.gjl.music.pipeline;

import com.gjl.music.config.ConfigChangedEvent;
import com.gjl.music.config.ConfigService;
import com.gjl.music.model.PipelineTask;
import com.gjl.music.pipeline.engine.FileProgressTracker;
import com.gjl.music.pipeline.engine.PauseController;
import com.gjl.music.pipeline.engine.PipelineEngine;
import com.gjl.music.pipeline.graph.GraphNode;
import com.gjl.music.pipeline.graph.PipelineGraph;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;


@Slf4j
@Service
public class PipelineOrchestrator {

    private final Executor pipelineExecutor;
    private final SimpMessagingTemplate messagingTemplate;
    private final PipelinePersistence persistence;
    private final PipelineFactory pipelineFactory;
    private final PipelineRecordStore recordStore;
    private final ConfigService configService;

    private final Map<String, PipelineEntry> pipelines = new ConcurrentHashMap<>();

    private volatile Semaphore concurrencySlot;

    private volatile boolean recoveryEnabled;
    private volatile int taskRetentionHours;
    private volatile int maxPipelines;

    public PipelineOrchestrator(@Qualifier("pipelineExecutor") Executor pipelineExecutor,
                                 SimpMessagingTemplate messagingTemplate,
                                 PipelinePersistence persistence,
                                 PipelineFactory pipelineFactory,
                                 PipelineRecordStore recordStore,
                                 ConfigService configService) {
        this.pipelineExecutor = pipelineExecutor;
        this.messagingTemplate = messagingTemplate;
        this.persistence = persistence;
        this.pipelineFactory = pipelineFactory;
        this.recordStore = recordStore;
        this.configService = configService;
    }


    private void reloadConfig() {
        this.recoveryEnabled = configService.getBoolean("pipeline.recovery.enabled", true);
        this.taskRetentionHours = configService.getInt("pipeline.task.retention_hours", 720);
        this.maxPipelines = configService.getInt("pipeline.executor.scheduler.max_pipelines", 1);
            }

    @EventListener
    public void onConfigChanged(ConfigChangedEvent e) {
        switch (e.configKey()) {
            case "pipeline.recovery.enabled" -> this.recoveryEnabled = e.asBoolean();
            case "pipeline.task.retention_hours" -> this.taskRetentionHours = e.asInt(720);
            case "pipeline.executor.scheduler.max_pipelines" -> {
                int newMax = e.asInt(1);
                if (concurrencySlot != null && newMax != this.maxPipelines) {
                                        int currentPermits = concurrencySlot.availablePermits();
                    int occupied = Math.max(0, this.maxPipelines - currentPermits);
                                        Semaphore newSlot = new Semaphore(Math.max(occupied, newMax));
                    if (occupied > 0 && occupied < newMax) {
                        newSlot.acquireUninterruptibly(occupied);
                    }
                    this.concurrencySlot = newSlot;
                    log.info("concurrencySlot replaced: {} → {} permits ({} occupied)", this.maxPipelines, newMax, occupied);
                }
                this.maxPipelines = newMax;
            }
        }
    }


    @PostConstruct
    public void recoverPipelines() {
        reloadConfig();
        this.concurrencySlot = new Semaphore(maxPipelines);
        if (!recoveryEnabled) {
            log.info("Pipeline recovery disabled");
            return;
        }
        List<PipelineTaskInfo> tasks;
        try {
            tasks = recordStore.loadNonTerminal();
        } catch (Exception e) {
            log.error("Failed to load non-terminal pipelines for recovery", e);
            return;
        }
        if (tasks.isEmpty()) {
            log.info("No incomplete pipelines to recover");
            return;
        }

        log.info("Recovering {} incomplete pipeline(s)...", tasks.size());
        for (PipelineTaskInfo task : tasks) {
            try {
                                if ("RUNNING".equals(task.getState()) || "READY".equals(task.getState())) {
                    log.info("Pipeline [{}] was {}, converting to PAUSED for safe recovery",
                            task.getPipelineId(), task.getState());
                    persistence.updateState(task.getPipelineId(), PipelineState.PAUSED);
                    recordStore.updateState(task.getPipelineId(), PipelineState.PAUSED);
                }

                log.info("Pipeline [{}] recovered as PAUSED (resume manually)", task.getPipelineId());
            } catch (Exception e) {
                log.error("Failed to recover pipeline [{}], marking FAILED", task.getPipelineId(), e);
                try {
                    persistence.updateState(task.getPipelineId(), PipelineState.FAILED);
                    recordStore.updateState(task.getPipelineId(), PipelineState.FAILED);
                } catch (Exception ex) {
                    log.error("Failed to mark pipeline [{}] as FAILED", task.getPipelineId(), ex);
                }
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down, {} active pipelines", pipelines.size());
                persistence.flushItemLogBuffer();
        for (PipelineEntry entry : pipelines.values()) {
            syncProgressToDb(entry);
            PipelineState state = entry.engine.getCurrentState();
            if (state == PipelineState.RUNNING || state == PipelineState.PAUSED) {
                persistence.updateState(entry.engine.getPipelineId(), PipelineState.PAUSED);
                recordStore.updateState(entry.engine.getPipelineId(), PipelineState.PAUSED);
            }
        }
    }


    public String submit(PipelineEngine engine, String templateName, String graphJson,
                          String[] relativePaths) {
        String id = engine.getPipelineId();

                persistence.insertTask(id, PipelineState.READY, templateName, graphJson, relativePaths);
        PipelineTaskInfo info = new PipelineTaskInfo();
        info.setPipelineId(id);
        info.setTemplateName(templateName != null ? templateName : "");
        info.setState(PipelineState.READY.name());
        info.setInputPaths(persistence.toJson(relativePaths));
        info.setCreatedAt(LocalDateTime.now().toString().replace("T", " ").substring(0, 19));
        recordStore.save(info);

        PipelineEntry entry = new PipelineEntry(engine, templateName,
                info.getInputPaths());
        pipelines.put(id, entry);

                entry.future = CompletableFuture.runAsync(() -> {
            try {
                concurrencySlot.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            try {
                persistence.updateState(id, PipelineState.RUNNING);
                recordStore.updateState(id, PipelineState.RUNNING);
                PipelineResult result = engine.execute();
                entry.durationMs = result.getDurationMs();

                                persistence.flushItemLogBuffer();

                                PipelineState finalState = result.isSuccess() ? PipelineState.COMPLETED : PipelineState.FAILED;
                persistence.updateState(id, finalState);
                persistence.updateProgress(id,
                        result.getTotalFiles(), result.getSuccessFiles(), result.getFailedFiles(),
                        result.getErrorNode(), result.getErrorMessage());
                recordStore.updateState(id, finalState);
                recordStore.updateProgress(id,
                        result.getTotalFiles(), result.getSuccessFiles(), result.getFailedFiles(),
                        result.getErrorNode(), result.getErrorMessage());

                log.info("Pipeline [{}] completed: state={}, {}/{} files",
                        id, finalState, result.getSuccessFiles(), result.getTotalFiles());

            } catch (Exception e) {
                log.error("Pipeline [{}] execution error", id, e);
                persistence.flushItemLogBuffer();
                persistence.updateState(id, PipelineState.FAILED);
                recordStore.updateState(id, PipelineState.FAILED);
                try {
                    FileProgressTracker tracker = engine.getProgressTracker();
                    persistence.updateProgress(id,
                            tracker.getTotalFiles(), tracker.getSuccessFiles(), tracker.getFailedFiles(),
                            "engine", e.getMessage());
                    recordStore.updateProgress(id,
                            tracker.getTotalFiles(), tracker.getSuccessFiles(), tracker.getFailedFiles(),
                            "engine", e.getMessage());
                } catch (Exception ex) {
                    log.error("Failed to persist error state for [{}]", id, ex);
                }
            } finally {
                if (!entry.slotReleased) {
                    concurrencySlot.release();
                }
                pipelineFactory.removeGraphJson(id);
            }
        }, pipelineExecutor);

        log.info("Pipeline [{}] submitted, total: {}", id, pipelines.size());
        return id;
    }


    public PipelineState getState(String pipelineId) {
        PipelineEntry entry = pipelines.get(pipelineId);
        if (entry != null) return entry.engine.getCurrentState();
        PipelineTask task = persistence.findTaskById(pipelineId);
        if (task == null) return null;
        try { return PipelineState.valueOf(task.getState()); }
        catch (IllegalArgumentException e) { return null; }
    }

    public PipelineProgress getProgress(String pipelineId) {
        PipelineEntry entry = pipelines.get(pipelineId);
        if (entry != null) {
            String createdAt = entry.createdAt.toString().replace("T", " ").substring(0, 19);
            PipelineState state = entry.engine.getCurrentState();
            long effectiveDuration;
            if (state == PipelineState.RUNNING) {
                                effectiveDuration = System.currentTimeMillis() - entry.startMs
                        - entry.engine.getPauseController().getTotalPausedMs();
            } else if (state == PipelineState.PAUSED) {
                                PauseController pc = entry.engine.getPauseController();
                long pauseStart = pc.getPauseStartMs();
                effectiveDuration = (pauseStart > 0 ? pauseStart : System.currentTimeMillis())
                        - entry.startMs - pc.getTotalPausedMs();
            } else {
                                effectiveDuration = entry.durationMs;
            }
            return entry.engine.getProgressTracker().snapshot(state, createdAt, effectiveDuration);
        }
        PipelineTask task = persistence.findTaskById(pipelineId);
        if (task == null) return null;
        String createdAt = task.getCreateTime() != null
                ? task.getCreateTime().toString().replace("T", " ").substring(0, 19)
                : "";
        return new PipelineProgress(
                pipelineId,
                PipelineState.valueOf(task.getState()),
                task.getTotalFiles() != null ? task.getTotalFiles() : 0,
                task.getSuccessFiles() != null ? task.getSuccessFiles() : 0,
                task.getFailedFiles() != null ? task.getFailedFiles() : 0,
                task.getErrorNode(),
                task.getErrorMessage(),
                createdAt
        );
    }


    public PipelineTaskInfo getTaskInfo(String pipelineId) {
        PipelineEntry entry = pipelines.get(pipelineId);
        if (entry != null) {
            PipelineProgress p = getProgress(pipelineId);
            PipelineTaskInfo info = PipelineTaskInfo.from(pipelineId, entry.templateName,
                    entry.engine.getCurrentState(), p);
            info.setInputPaths(entry.inputPaths != null ? entry.inputPaths : "");
            info.setCreatedAt(entry.createdAt.toString().replace("T", " ").substring(0, 19));
            return info;
        }
        return recordStore.findById(pipelineId);
    }

    public boolean pause(String pipelineId) {
        PipelineEntry entry = pipelines.get(pipelineId);
        if (entry == null) {
            log.warn("Cannot pause pipeline [{}]: not found", pipelineId);
            return false;
        }
        PipelineState state = entry.engine.getCurrentState();
        if (state != PipelineState.RUNNING) {
            log.warn("Cannot pause pipeline [{}]: state={}", pipelineId, state);
            return false;
        }
        log.info("Pausing pipeline [{}], releasing execution slot", pipelineId);
                persistence.flushItemLogBuffer();
        syncProgressToDb(entry);
        entry.engine.getPauseController().pause();
        entry.engine.setCurrentState(PipelineState.PAUSED);
        persistence.updateState(pipelineId, PipelineState.PAUSED);
        recordStore.updateState(pipelineId, PipelineState.PAUSED);
        if (!entry.slotReleased) {
            concurrencySlot.release();
            entry.slotReleased = true;
        }
        return true;
    }

    public boolean resume(String pipelineId) {
        PipelineEntry entry = pipelines.get(pipelineId);
        if (entry == null) {
                        return resumeFromDb(pipelineId);
        }
        PipelineState currentState = entry.engine.getCurrentState();
        if (currentState != PipelineState.PAUSED && currentState != PipelineState.READY) {
            log.warn("Cannot resume pipeline [{}]: state={}", pipelineId, currentState);
            return false;
        }
        if (entry.slotReleased) {
                        if (concurrencySlot.tryAcquire()) {
                entry.slotReleased = false;
            } else {
                log.info("Resuming pipeline [{}] but no slot available, queuing as READY", pipelineId);
                entry.engine.setCurrentState(PipelineState.READY);
                persistence.updateState(pipelineId, PipelineState.READY);
                recordStore.updateState(pipelineId, PipelineState.READY);
                                CompletableFuture.runAsync(() -> {
                    try {
                        concurrencySlot.acquire();
                        entry.slotReleased = false;
                        entry.engine.getPauseController().resume();
                        entry.engine.setCurrentState(PipelineState.RUNNING);
                        persistence.updateState(pipelineId, PipelineState.RUNNING);
                        recordStore.updateState(pipelineId, PipelineState.RUNNING);
                        log.info("Pipeline [{}] resumed from READY queue", pipelineId);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }, pipelineExecutor);
                return true;
            }
        }
        log.info("Resuming pipeline [{}]", pipelineId);
        entry.engine.getPauseController().resume();
        entry.engine.setCurrentState(PipelineState.RUNNING);
        persistence.updateState(pipelineId, PipelineState.RUNNING);
        recordStore.updateState(pipelineId, PipelineState.RUNNING);
        return true;
    }


    private boolean resumeFromDb(String pipelineId) {
        PipelineTask task = persistence.findTaskById(pipelineId);
        if (task == null) {
            log.warn("Cannot resume pipeline [{}]: not found in DB", pipelineId);
            return false;
        }

        PipelineState state;
        try {
            state = PipelineState.valueOf(task.getState());
        } catch (IllegalArgumentException e) {
            log.warn("Cannot resume pipeline [{}]: unknown state [{}]", pipelineId, task.getState());
            return false;
        }

        if (state != PipelineState.PAUSED && state != PipelineState.READY) {
            log.warn("Cannot resume pipeline [{}]: DB state is {}, not PAUSED/READY", pipelineId, state);
            return false;
        }

                PipelineGraph graph = rebuildGraph(task.getGraphJson());
        if (graph == null) {
            log.error("Cannot resume [{}]: failed to parse graphJson", pipelineId);
            persistence.updateState(pipelineId, PipelineState.FAILED);
            recordStore.updateState(pipelineId, PipelineState.FAILED);
            return false;
        }

                String[] paths = persistence.fromJson(task.getInputPaths(), String[].class);
        if (paths == null) paths = new String[0];
        final String[] finalPaths = paths;

                Set<String> successKeys = persistence.selectSuccessKeys(pipelineId);
        log.info("Resuming pipeline [{}] from DB: template={}, {} paths, {} already done",
                pipelineId, task.getTemplateName(), finalPaths.length, successKeys.size());

                PipelineEngine engine = pipelineFactory.createFromGraph(graph, pipelineId,
                ctx -> {
                    Path[] resolved = Arrays.stream(finalPaths)
                            .map(pipelineFactory::resolvePath)
                            .toArray(Path[]::new);
                    ctx.setSlot("input.paths", resolved);
                    ctx.setSlot("resume.skipKeys", successKeys);
                },
                this::pushItemLog);

                FileProgressTracker tracker = engine.getProgressTracker();
        if (task.getTotalFiles() != null && task.getTotalFiles() > 0) {
            tracker.setTotalFiles(task.getTotalFiles());
        }
        for (String key : successKeys) {
            tracker.markFileComplete(key, true, null);
        }

                PipelineEntry entry = new PipelineEntry(engine,
                task.getTemplateName() != null ? task.getTemplateName() : "",
                task.getInputPaths() != null ? task.getInputPaths() : "[]");
        entry.slotReleased = false;
        pipelines.put(pipelineId, entry);

                entry.future = CompletableFuture.runAsync(() -> {
            try {
                concurrencySlot.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            try {
                persistence.updateState(pipelineId, PipelineState.RUNNING);
                recordStore.updateState(pipelineId, PipelineState.RUNNING);
                engine.setCurrentState(PipelineState.RUNNING);

                PipelineResult result = engine.execute();
                entry.durationMs = result.getDurationMs();

                persistence.flushItemLogBuffer();

                PipelineState finalState = result.isSuccess()
                        ? PipelineState.COMPLETED : PipelineState.FAILED;
                persistence.updateState(pipelineId, finalState);
                persistence.updateProgress(pipelineId,
                        result.getTotalFiles(), result.getSuccessFiles(),
                        result.getFailedFiles(),
                        result.getErrorNode(), result.getErrorMessage());
                recordStore.updateState(pipelineId, finalState);
                recordStore.updateProgress(pipelineId,
                        result.getTotalFiles(), result.getSuccessFiles(),
                        result.getFailedFiles(),
                        result.getErrorNode(), result.getErrorMessage());

                log.info("Pipeline [{}] resumed → completed: state={}, {}/{} files",
                        pipelineId, finalState,
                        result.getSuccessFiles(), result.getTotalFiles());

            } catch (Exception e) {
                log.error("Pipeline [{}] resumed execution error", pipelineId, e);
                persistence.flushItemLogBuffer();
                persistence.updateState(pipelineId, PipelineState.FAILED);
                recordStore.updateState(pipelineId, PipelineState.FAILED);
                try {
                    persistence.updateProgress(pipelineId,
                            tracker.getTotalFiles(), tracker.getSuccessFiles(),
                            tracker.getFailedFiles(), "engine", e.getMessage());
                    recordStore.updateProgress(pipelineId,
                            tracker.getTotalFiles(), tracker.getSuccessFiles(),
                            tracker.getFailedFiles(), "engine", e.getMessage());
                } catch (Exception ex) {
                    log.error("Failed to persist error state for [{}]", pipelineId, ex);
                }
            } finally {
                if (!entry.slotReleased) {
                    concurrencySlot.release();
                }
                pipelineFactory.removeGraphJson(pipelineId);
            }
        }, pipelineExecutor);

        log.info("Pipeline [{}] restored from DB and resumed, total active: {}",
                pipelineId, pipelines.size());
        return true;
    }


    @SuppressWarnings("unchecked")
    private PipelineGraph rebuildGraph(String graphJson) {
        if (graphJson == null || graphJson.isBlank()) return null;
        try {
            List<Map<String, Object>> defs = persistence.fromJson(graphJson,
                    new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});
            if (defs == null || defs.isEmpty()) return null;

            List<GraphNode> nodes = new ArrayList<>();
            for (Map<String, Object> def : defs) {
                String id = (String) def.get("id");
                String moduleName = (String) def.get("moduleName");
                List<String> deps = (List<String>) def.get("dependencies");
                Boolean finalNode = (Boolean) def.get("finalNode");
                Map<String, Object> config = (Map<String, Object>) def.get("config");

                GraphNode.Builder builder = GraphNode.builder(id)
                        .moduleName(moduleName != null ? moduleName : id)
                        .finalNode(finalNode != null && finalNode);
                if (deps != null) {
                    for (String d : deps) builder.dependsOn(d);
                }
                if (config != null && !config.isEmpty()) {
                    builder.configAll(config);
                }
                nodes.add(builder.build());
            }
            return new PipelineGraph(nodes);
        } catch (Exception e) {
            log.error("Failed to rebuild PipelineGraph from graphJson", e);
            return null;
        }
    }

    public boolean cancel(String pipelineId) {
        PipelineEntry entry = pipelines.get(pipelineId);
        if (entry == null) {
            log.warn("Cannot cancel pipeline [{}]: not found", pipelineId);
            return false;
        }
        PipelineState currentState = entry.engine.getCurrentState();
        if (currentState == PipelineState.COMPLETED
                || currentState == PipelineState.FAILED
                || currentState == PipelineState.CANCELLED) {
            log.warn("Cannot cancel pipeline [{}]: already terminal {}", pipelineId, currentState);
            return false;
        }
        log.info("Cancelling pipeline [{}], current state={}", pipelineId, currentState);
                persistence.flushItemLogBuffer();
        syncProgressToDb(entry);
        entry.engine.getPauseController().cancel();
        entry.engine.setCurrentState(PipelineState.CANCELLED);
        if (entry.future != null) {
            entry.future.cancel(true);
        }
        if (!entry.slotReleased) {
            concurrencySlot.release();
            entry.slotReleased = true;
        }
        persistence.updateState(pipelineId, PipelineState.CANCELLED);
        recordStore.updateState(pipelineId, PipelineState.CANCELLED);
        return true;
    }


    public List<Map<String, Object>> listAll() {
                List<PipelineTaskInfo> records = recordStore.listAll();

                for (PipelineTaskInfo info : records) {
            PipelineEntry entry = pipelines.get(info.getPipelineId());
            if (entry != null) {
                PipelineState liveState = entry.engine.getCurrentState();
                PipelineProgress p = getProgress(entry.engine.getPipelineId());
                info.setState(liveState.name());
                info.setTotalFiles(p.getTotalFiles());
                info.setSuccessFiles(p.getSuccessFiles());
                info.setFailedFiles(p.getFailedFiles());
                info.setErrorModule(p.getErrorNode() != null ? p.getErrorNode() : "");
                info.setErrorMessage(p.getErrorMessage() != null ? p.getErrorMessage() : "");
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (PipelineTaskInfo info : records) {
            result.add(taskInfoToMap(info));
        }
        return result;
    }

    private static Map<String, Object> taskInfoToMap(PipelineTaskInfo info) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("pipelineId", info.getPipelineId());
        map.put("templateName", info.getTemplateName());
        map.put("state", info.getState());
        map.put("totalFiles", info.getTotalFiles());
        map.put("successFiles", info.getSuccessFiles());
        map.put("failedFiles", info.getFailedFiles());
        map.put("createdAt", info.getCreatedAt());
        map.put("errorModule", info.getErrorModule());
        map.put("errorMessage", info.getErrorMessage());
        map.put("inputPaths", info.getInputPaths());
        return map;
    }


    @Scheduled(fixedRate = 1000)
    public void pushProgress() {
        for (PipelineEntry entry : pipelines.values()) {
            PipelineState state = entry.engine.getCurrentState();

            if (state == PipelineState.RUNNING || state == PipelineState.PAUSED) {
                PipelineProgress progress = getProgress(entry.engine.getPipelineId());
                String destination = "/topic/pipelines/" + entry.engine.getPipelineId() + "/progress";
                messagingTemplate.convertAndSend(destination, progress);

            } else if (state == PipelineState.COMPLETED || state == PipelineState.FAILED
                    || state == PipelineState.CANCELLED) {
                if (!entry.finalEventSent) {
                    PipelineProgress progress = getProgress(entry.engine.getPipelineId());
                    String destination = "/topic/pipelines/" + entry.engine.getPipelineId() + "/progress";
                    messagingTemplate.convertAndSend(destination, progress);
                    entry.finalEventSent = true;
                }
            }
        }
    }

    @Scheduled(cron = "0 */30 * * * *")
    public void cleanupExpired() {
        LocalDateTime horizon = LocalDateTime.now().minusHours(taskRetentionHours);

                pipelines.entrySet().removeIf(entry -> {
            PipelineState state = entry.getValue().engine.getCurrentState();
            boolean isTerminal = state == PipelineState.COMPLETED
                    || state == PipelineState.FAILED
                    || state == PipelineState.CANCELLED;
            return isTerminal && entry.getValue().createdAt.isBefore(horizon);
        });

                recordStore.cleanupExpired(horizon);
    }


    public void pushItemLog(com.gjl.music.model.PipelineItemLog item) {
        String destination = "/topic/pipelines/" + item.getPipelineId() + "/items";
        messagingTemplate.convertAndSend(destination, item);
    }


    public Map<String, Object> getPipelineItems(String pipelineId, String status, int page, int size) {
        var logs = persistence.selectItemLogs(pipelineId, status, page, size);
        int total = persistence.countItemLogs(pipelineId, status);
        return Map.of("items", logs, "total", total, "page", page, "size", size);
    }


    private void syncProgressToDb(PipelineEntry entry) {
        try {
            FileProgressTracker tracker = entry.engine.getProgressTracker();
            String pipelineId = entry.engine.getPipelineId();
            persistence.updateProgress(pipelineId,
                    tracker.getTotalFiles(), tracker.getSuccessFiles(), tracker.getFailedFiles(),
                    null, null);
            recordStore.updateProgress(pipelineId,
                    tracker.getTotalFiles(), tracker.getSuccessFiles(), tracker.getFailedFiles(),
                    null, null);
        } catch (Exception e) {
            log.error("Failed to sync progress for [{}]", entry.engine.getPipelineId(), e);
        }
    }

    private static class PipelineEntry {
        final PipelineEngine engine;
        final String templateName;
        final String inputPaths;
        final LocalDateTime createdAt;
        final long startMs;
        Future<?> future;
        boolean finalEventSent;
        volatile boolean slotReleased;
        volatile long durationMs;

        PipelineEntry(PipelineEngine engine, String templateName, String inputPaths) {
            this.engine = engine;
            this.templateName = templateName;
            this.inputPaths = inputPaths;
            this.createdAt = LocalDateTime.now();
            this.startMs = System.currentTimeMillis();
        }
    }

}
