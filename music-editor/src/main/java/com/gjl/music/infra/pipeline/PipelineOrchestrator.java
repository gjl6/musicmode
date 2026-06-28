package com.gjl.music.infra.pipeline;

import com.gjl.music.config.ConfigChangedEvent;
import com.gjl.music.config.ConfigService;
import com.gjl.music.model.PipelineTask;
import com.gjl.music.infra.pipeline.engine.FileProgressTracker;
import com.gjl.music.infra.pipeline.engine.PauseController;
import com.gjl.music.infra.pipeline.engine.PipelineEngine;
import com.gjl.music.infra.pipeline.graph.GraphNode;
import com.gjl.music.infra.pipeline.graph.PipelineGraph;
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

/**
 * 管道运行时管理器 —— 异步提交、生命周期控制、进度推送、崩溃恢复。
 *
 * <p>核心设计：
 * <ul>
 *   <li>Semaphore 控制最大并发管道数（可配置）</li>
 *   <li>pipelineExecutor 承载管道调度线程</li>
 *   <li>WebSocket 每秒推送进度</li>
 *   <li>启动时自动恢复非终态管道</li>
 * </ul>
 */
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
    /** WS 条目缓冲：攒批发送，减少 STOMP 消息数量。每管道上限 200 条 */
    private final Map<String, WsItemBuffer> wsItemBuffers = new ConcurrentHashMap<>();
    private static final int WS_ITEM_CAP = 200;
    /** 信号量控制最大并发管道数，许可数可通过配置热更新 */
    private volatile Semaphore concurrencySlot;

    private volatile boolean recoveryEnabled;
    private volatile boolean autoResume;
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

    // ═══════════════════════════════════════════════════════════════
    // 配置热更新
    // ═══════════════════════════════════════════════════════════════

    private void reloadConfig() {
        this.recoveryEnabled = configService.getBoolean("pipeline.recovery.enabled", true);
        this.autoResume = configService.getBoolean("pipeline.recovery.auto_resume", false);
        this.taskRetentionHours = configService.getInt("pipeline.task.retention_hours", 720);
        this.maxPipelines = configService.getInt("pipeline.executor.scheduler.max_pipelines", 1);
        // 信号量在构造后首次生成，热更新时通过 onConfigChanged 调整许可数
    }

    @EventListener
    public void onConfigChanged(ConfigChangedEvent e) {
        switch (e.configKey()) {
            case "pipeline.recovery.enabled" -> this.recoveryEnabled = e.asBoolean();
            case "pipeline.recovery.auto_resume" -> this.autoResume = e.asBoolean();
            case "pipeline.task.retention_hours" -> this.taskRetentionHours = e.asInt(720);
            case "pipeline.executor.scheduler.max_pipelines" -> {
                int newMax = e.asInt(1);
                if (concurrencySlot != null && newMax != this.maxPipelines) {
                    // 计算当前已占用许可数
                    int currentPermits = concurrencySlot.availablePermits();
                    int occupied = Math.max(0, this.maxPipelines - currentPermits);
                    // 创建新 Semaphore，预占用当前运行中的管道数
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

    // ═══════════════════════════════════════════════════════════════
    // 启动恢复
    // ═══════════════════════════════════════════════════════════════

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

        log.info("Recovering {} incomplete pipeline(s)... (autoResume={})",
                tasks.size(), autoResume);
        int autoResumed = 0;
        int paused = 0;

        for (PipelineTaskInfo task : tasks) {
            try {
                String state = task.getState();
                if ("RUNNING".equals(state) || "READY".equals(state)) {
                    if (autoResume) {
                        // 自动恢复：原状态是什么就恢复成什么，提交到 executor 执行
                        log.info("Pipeline [{}] was {}, auto-resuming...",
                                task.getPipelineId(), state);
                        boolean ok = resumeFromDb(task.getPipelineId());
                        if (ok) {
                            autoResumed++;
                        } else {
                            // resumeFromDb 失败 → 降级为 PAUSED
                            persistence.updateState(task.getPipelineId(), PipelineState.PAUSED);
                            recordStore.updateState(task.getPipelineId(), PipelineState.PAUSED);
                            paused++;
                            log.warn("Pipeline [{}] auto-resume failed, converted to PAUSED",
                                    task.getPipelineId());
                        }
                    } else {
                        // 默认：安全保守，转为 PAUSED 等待手动恢复
                        log.info("Pipeline [{}] was {}, converting to PAUSED for safe recovery",
                                task.getPipelineId(), state);
                        persistence.updateState(task.getPipelineId(), PipelineState.PAUSED);
                        recordStore.updateState(task.getPipelineId(), PipelineState.PAUSED);
                        paused++;
                    }
                } else if ("PAUSED".equals(state)) {
                    // PAUSED 始终保持 PAUSED（用户主动暂停的）
                    log.info("Pipeline [{}] was PAUSED, keeping PAUSED", task.getPipelineId());
                    paused++;
                }
                // 其他状态（FAILED/CANCELLED/COMPLETED）不会出现在 loadNonTerminal 结果中
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

        log.info("Recovery complete: {} auto-resumed, {} paused", autoResumed, paused);
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down, {} active pipelines", pipelines.size());
        // 刷盘所有活跃管道的 item_log + 进度
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

    // ═══════════════════════════════════════════════════════════════
    // 提交
    // ═══════════════════════════════════════════════════════════════

    /**
     * 异步提交管道，立即返回 pipelineId。
     */
    public String submit(PipelineEngine engine, String templateName, String graphJson,
                          String[] relativePaths) {
        String id = engine.getPipelineId();

        // 持久化初始记录（DB + Redis）
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

        // 异步执行
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

                // 刷盘 item logs
                persistence.flushItemLogBuffer();

                // 持久化最终状态
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
                drainAndRemoveWsBuffer(id);
            }
        }, pipelineExecutor);

        log.info("Pipeline [{}] submitted, total: {}", id, pipelines.size());
        return id;
    }

    // ═══════════════════════════════════════════════════════════════
    // 生命周期控制
    // ═══════════════════════════════════════════════════════════════

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
                // 运行中：墙上时间 - 累计已完成暂停
                effectiveDuration = System.currentTimeMillis() - entry.startMs
                        - entry.engine.getPauseController().getTotalPausedMs();
            } else if (state == PipelineState.PAUSED) {
                // 暂停中：冻结在暂停那一刻（扣除已完成 + 进行中的暂停）
                PauseController pc = entry.engine.getPauseController();
                long pauseStart = pc.getPauseStartMs();
                effectiveDuration = (pauseStart > 0 ? pauseStart : System.currentTimeMillis())
                        - entry.startMs - pc.getTotalPausedMs();
            } else {
                // 终态：durationMs 已由 PipelineEngine 扣除暂停时长
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

    /** 获取管道任务记录（含 templateName） */
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
        // 立即刷盘 item_log 缓冲 + 更新进度，保证暂停时 DB 状态最新
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
            // 内存中没有 → 尝试从 DB 恢复（服务重启后场景）
            return resumeFromDb(pipelineId);
        }
        PipelineState currentState = entry.engine.getCurrentState();
        if (currentState != PipelineState.PAUSED && currentState != PipelineState.READY) {
            log.warn("Cannot resume pipeline [{}]: state={}", pipelineId, currentState);
            return false;
        }
        if (entry.slotReleased) {
            // 非阻塞尝试获取槽位；无槽位时设为 READY 排队，不阻塞 API 线程
            if (concurrencySlot.tryAcquire()) {
                entry.slotReleased = false;
            } else {
                log.info("Resuming pipeline [{}] but no slot available, queuing as READY", pipelineId);
                entry.engine.setCurrentState(PipelineState.READY);
                persistence.updateState(pipelineId, PipelineState.READY);
                recordStore.updateState(pipelineId, PipelineState.READY);
                // 异步等待槽位，获取后真正恢复执行
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

    /**
     * 从 DB 恢复并重新提交管道（服务重启后 resume 场景）。
     *
     * <p>从 graphJson 重建 PipelineEngine，从 item_log 查询已成功文件跳过，
     * 以新的 CompletableFuture 异步执行。
     */
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

        // autoResume 时允许 RUNNING 状态（崩溃未执行 shutdown hook）
        if (state != PipelineState.PAUSED && state != PipelineState.READY
                && state != PipelineState.RUNNING) {
            log.warn("Cannot resume pipeline [{}]: DB state is {}, not RUNNING/PAUSED/READY",
                    pipelineId, state);
            return false;
        }

        // 1. 从 graphJson 重建 PipelineGraph
        PipelineGraph graph = rebuildGraph(task.getGraphJson());
        if (graph == null) {
            log.error("Cannot resume [{}]: failed to parse graphJson", pipelineId);
            persistence.updateState(pipelineId, PipelineState.FAILED);
            recordStore.updateState(pipelineId, PipelineState.FAILED);
            return false;
        }

        // 2. 解析 inputPaths
        String[] paths = persistence.fromJson(task.getInputPaths(), String[].class);
        if (paths == null) paths = new String[0];
        final String[] finalPaths = paths;

        // 3. 查询已成功处理的文件（checkpoint 跳过）
        Set<String> successKeys = persistence.selectSuccessKeys(pipelineId);
        log.info("Resuming pipeline [{}] from DB: template={}, {} paths, {} already done",
                pipelineId, task.getTemplateName(), finalPaths.length, successKeys.size());

        // 4. 创建新 Engine
        PipelineEngine engine = pipelineFactory.createFromGraph(graph, pipelineId,
                ctx -> {
                    Path[] resolved = Arrays.stream(finalPaths)
                            .map(pipelineFactory::resolvePath)
                            .toArray(Path[]::new);
                    ctx.setSlot("input.paths", resolved);
                    ctx.setSlot("resume.skipKeys", successKeys);
                },
                this::pushItemLog);

        // 5. 恢复进度（预填已完成文件）
        FileProgressTracker tracker = engine.getProgressTracker();
        if (task.getTotalFiles() != null && task.getTotalFiles() > 0) {
            tracker.setTotalFiles(task.getTotalFiles());
        }
        for (String key : successKeys) {
            tracker.markFileComplete(key, true, null);
        }

        // 6. 创建 PipelineEntry 并加入内存
        PipelineEntry entry = new PipelineEntry(engine,
                task.getTemplateName() != null ? task.getTemplateName() : "",
                task.getInputPaths() != null ? task.getInputPaths() : "[]");
        entry.slotReleased = false; // 即将执行，先占槽
        pipelines.put(pipelineId, entry);

        // 7. 异步执行（与 submit() 相同模式，但不重新 insert task）
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
                drainAndRemoveWsBuffer(pipelineId);
            }
        }, pipelineExecutor);

        log.info("Pipeline [{}] restored from DB and resumed, total active: {}",
                pipelineId, pipelines.size());
        return true;
    }

    /**
     * 从持久化的 graphJson 重建 PipelineGraph。
     *
     * <p>graphJson 格式：{@code [{"id":"...","moduleName":"...","dependencies":[...],"finalNode":bool,"config":{...}},...]}
     */
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
        // 立即刷盘 item_log 缓冲 + 更新进度
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

    // ═══════════════════════════════════════════════════════════════
    // 列表查询
    // ═══════════════════════════════════════════════════════════════

    public List<Map<String, Object>> listAll() {
        // DB 全量（永久记录，操作日志主源）+ Redis 活跃覆盖实时进度
        List<PipelineTaskInfo> records = recordStore.listAll();

        // 用内存中运行中管道的实时数据覆盖（进度比 DB/Redis 都新）
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

    // ═══════════════════════════════════════════════════════════════
    // 定时任务
    // ═══════════════════════════════════════════════════════════════

    @Scheduled(fixedRate = 1000)
    public void pushProgress() {
        for (PipelineEntry entry : pipelines.values()) {
            String id = entry.engine.getPipelineId();
            PipelineState state = entry.engine.getCurrentState();

            if (state == PipelineState.RUNNING || state == PipelineState.PAUSED) {
                PipelineProgress progress = getProgress(id);
                messagingTemplate.convertAndSend("/topic/pipelines/" + id + "/progress", progress);

            } else if (state == PipelineState.COMPLETED || state == PipelineState.FAILED
                    || state == PipelineState.CANCELLED) {
                if (!entry.finalEventSent) {
                    PipelineProgress progress = getProgress(id);
                    messagingTemplate.convertAndSend("/topic/pipelines/" + id + "/progress", progress);
                    entry.finalEventSent = true;
                }
            }

            // ── 批量发送 WS 条目缓冲 ──
            flushWsItemBuffer(id);
        }
    }

    @Scheduled(cron = "0 */30 * * * *")
    public void cleanupExpired() {
        LocalDateTime horizon = LocalDateTime.now().minusHours(taskRetentionHours);

        // 内存清理
        pipelines.entrySet().removeIf(entry -> {
            PipelineState state = entry.getValue().engine.getCurrentState();
            boolean isTerminal = state == PipelineState.COMPLETED
                    || state == PipelineState.FAILED
                    || state == PipelineState.CANCELLED;
            return isTerminal && entry.getValue().createdAt.isBefore(horizon);
        });

        // Redis + DB 清理
        recordStore.cleanupExpired(horizon);
    }

    // ═══════════════════════════════════════════════════════════════
    // 文件级实时推送
    // ═══════════════════════════════════════════════════════════════

    /**
     * 推送单个文件处理结果到 WS 缓冲池，由 {@link #pushProgress()} 每秒批量发送。
     * 缓冲池始终保持最新 200 条（FIFO 滚动窗口）。
     */
    public void pushItemLog(com.gjl.music.model.PipelineItemLog item) {
        WsItemBuffer buf = wsItemBuffers.computeIfAbsent(
                item.getPipelineId(), k -> new WsItemBuffer());
        synchronized (buf) {
            buf.items.add(item);
            while (buf.items.size() > WS_ITEM_CAP) {
                buf.items.removeFirst();
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 分页查询 item log
    // ═══════════════════════════════════════════════════════════════

    public Map<String, Object> getPipelineItems(String pipelineId, String status, int page, int size) {
        var logs = persistence.selectItemLogs(pipelineId, status, page, size);
        int total = persistence.countItemLogs(pipelineId, status);
        return Map.of("items", logs, "total", total, "page", page, "size", size);
    }

    // ═══════════════════════════════════════════════════════════════
    // 内部
    // ═══════════════════════════════════════════════════════════════

    /** 将内存中的实时进度同步写入 DB + Redis */
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

    // ── WS 条目批量发送 ──

    /** 每秒发送一次：将缓冲池的完整快照批量推送到前端 */
    private void flushWsItemBuffer(String pipelineId) {
        WsItemBuffer buf = wsItemBuffers.get(pipelineId);
        if (buf == null) return;
        List<com.gjl.music.model.PipelineItemLog> snapshot;
        synchronized (buf) {
            if (buf.items.isEmpty()) return;
            // 逆序：缓冲区 FIFO（旧→新），发送前反转为 LIFO（新→旧）与 DB ORDER BY id DESC 一致
            List<com.gjl.music.model.PipelineItemLog> reversed = new ArrayList<>(buf.items);
            Collections.reverse(reversed);
            snapshot = List.copyOf(reversed);
        }
        String destination = "/topic/pipelines/" + pipelineId + "/items";
        Object payload = Map.of("items", snapshot, "count", snapshot.size());
        messagingTemplate.convertAndSend(destination, payload);
    }

    /** 管道结束时刷空 WS 缓冲并清理 */
    private void drainAndRemoveWsBuffer(String pipelineId) {
        WsItemBuffer buf = wsItemBuffers.remove(pipelineId);
        if (buf == null) return;
        List<com.gjl.music.model.PipelineItemLog> snapshot;
        synchronized (buf) {
            if (buf.items.isEmpty()) return;
            List<com.gjl.music.model.PipelineItemLog> reversed = new ArrayList<>(buf.items);
            Collections.reverse(reversed);
            snapshot = List.copyOf(reversed);
        }
        String destination = "/topic/pipelines/" + pipelineId + "/items";
        Object payload = Map.of("items", snapshot, "count", snapshot.size(), "final", true);
        messagingTemplate.convertAndSend(destination, payload);
    }

    private static class WsItemBuffer {
        final ArrayList<com.gjl.music.model.PipelineItemLog> items = new ArrayList<>();
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
