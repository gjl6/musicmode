package com.gjl.music.watch;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 自动任务调度器 — 管理每个任务的独立调度生命周期。
 *
 * <p>每个启用扫描的任务持有 0~2 个 {@link ScheduledFuture}（DIR_SCAN + FILE_SCAN），
 * 按各自配置的间隔独立执行。通过 {@link TaskStore} 加载配置，通过 {@link AutoTaskRunner} 执行扫描。
 *
 * <p>位于 common，模块通过实现 {@link TaskStore} / {@link SongDbAccessor} / {@link FileChangeHandler}
 * 提供各自的逻辑。
 */
@Slf4j
@Service
public class AutoTaskScheduler implements ApplicationRunner, SmartLifecycle {

    private final AutoTaskRunner runner;
    private final TaskStore taskStore;
    private final ApplicationEventPublisher eventPublisher;

    private final ScheduledExecutorService scheduler;
    private final ConcurrentHashMap<Long, TaskHandles> taskHandles = new ConcurrentHashMap<>();

    /** 暂停状态：true = 跳过本轮扫描 */
    private final ConcurrentHashMap<Long, Boolean> pausedTasks = new ConcurrentHashMap<>();

    /** 目录时间戳快照：taskId → (dirPath → lastModified)，DIR_SCAN 跳级用 */
    final ConcurrentHashMap<Long, ConcurrentHashMap<String, Long>> dirTimestamps = new ConcurrentHashMap<>();

    /** 获取指定任务的目录时间戳缓存（供手动扫描使用） */
    public ConcurrentHashMap<String, Long> getDirTimestamps(Long taskId) {
        return dirTimestamps.computeIfAbsent(taskId, k -> new ConcurrentHashMap<>());
    }

    private volatile boolean running = false;
    private final AtomicBoolean shutdownDone = new AtomicBoolean(false);

    record TaskHandles(ScheduledFuture<?> dirScan, ScheduledFuture<?> fileScan,
                       ScheduledFuture<?> dataPipeline) {}

    public AutoTaskScheduler(AutoTaskRunner runner, TaskStore taskStore,
                              ApplicationEventPublisher eventPublisher) {
        this.runner = runner;
        this.taskStore = taskStore;
        this.eventPublisher = eventPublisher;
        this.scheduler = Executors.newScheduledThreadPool(
                Math.max(2, Runtime.getRuntime().availableProcessors()),
                r -> {
                    Thread t = new Thread(r);
                    t.setDaemon(true);
                    t.setName("task-sched");
                    return t;
                });
    }

    // ═══════════════════════════════════════════════════════════════
    // 生命周期
    // ═══════════════════════════════════════════════════════════════

    @Override
    public void run(ApplicationArguments args) {
        List<? extends TaskInfo> allTasks = taskStore.loadAll();
        int registered = 0;
        for (TaskInfo task : allTasks) {
            if (task.isAnyScanEnabled()) {
                registerTask(task);
                registered++;
            } else if (task.isDataDriven()) {
                registerDataDrivenTask(task);
                registered++;
            }
        }
        running = true;
        log.info("AutoTaskScheduler 启动: 已注册 {}/{} 个任务", registered, allTasks.size());
    }

    @Override
    public int getPhase() { return Integer.MAX_VALUE - 1; }

    @Override
    public void start() { /* ApplicationRunner 统一管理 */ }

    @Override
    public void stop() { doShutdown(); }

    @PreDestroy
    public void preDestroy() { doShutdown(); }

    private void doShutdown() {
        if (!shutdownDone.compareAndSet(false, true)) return;
        running = false;
        for (TaskHandles handles : taskHandles.values()) {
            cancelFuture(handles.dirScan);
            cancelFuture(handles.fileScan);
            cancelFuture(handles.dataPipeline);
        }
        taskHandles.clear();
        scheduler.shutdownNow();
        log.info("AutoTaskScheduler 已停止");
    }

    private static void cancelFuture(ScheduledFuture<?> f) {
        if (f != null && !f.isDone()) f.cancel(false);
    }

    @Override
    public boolean isRunning() { return running; }

    // ═══════════════════════════════════════════════════════════════
    // 公开方法
    // ═══════════════════════════════════════════════════════════════

    /** 注册新任务（create 后调用） */
    public void registerTask(TaskInfo task) {
        if (!task.isEnabled()) return;
        if (!task.isAnyScanEnabled() && !task.isDataDriven()) return;
        taskHandles.compute(task.getId(), (id, old) -> {
            cancelHandles(old);
            return createHandles(task);
        });
        if (task.isAnyScanEnabled()) {
            log.info("任务 '{}' (id={}) 已注册: dirScan={}/{}s, fileScan={}/{}s",
                    task.getName(), task.getId(),
                    task.isDirScanEnabled(), task.getDirScanIntervalSec(),
                    task.isFileScanEnabled(), task.getFileScanIntervalSec());
        } else {
            log.info("数据驱动任务 '{}' (id={}) 已注册: interval={}s",
                    task.getName(), task.getId(),
                    task.getDirScanIntervalSec() > 0 ? task.getDirScanIntervalSec() : 3600);
        }
    }

    /** 注册纯数据驱动任务 */
    public void registerDataDrivenTask(TaskInfo task) {
        if (!task.isEnabled()) return;
        taskHandles.compute(task.getId(), (id, old) -> {
            cancelHandles(old);
            return createDataDrivenHandle(task);
        });
        log.info("数据驱动任务 '{}' (id={}) 已注册: interval={}s",
                task.getName(), task.getId(),
                task.getDirScanIntervalSec() > 0 ? task.getDirScanIntervalSec() : 3600);
    }

    /** 更新任务配置 */
    public void updateTask(TaskInfo task) {
        unregisterTask(task.getId());
        if (task.isEnabled()) {
            if (task.isAnyScanEnabled()) {
                registerTask(task);
            } else if (task.isDataDriven()) {
                registerDataDrivenTask(task);
            }
        }
    }

    /** 注销任务 */
    public void unregisterTask(Long taskId) {
        TaskHandles old = taskHandles.remove(taskId);
        cancelHandles(old);
        pausedTasks.remove(taskId);
        dirTimestamps.remove(taskId);
        log.debug("任务 id={} 已注销", taskId);
    }

    /** 暂停 */
    public void pauseTask(Long taskId) {
        pausedTasks.put(taskId, true);
        log.info("任务 id={} 已暂停", taskId);
    }

    /** 恢复 */
    public void resumeTask(Long taskId) {
        pausedTasks.remove(taskId);
        log.info("任务 id={} 已恢复", taskId);
    }

    public boolean isPaused(Long taskId) {
        return pausedTasks.getOrDefault(taskId, false);
    }

    // ═══════════════════════════════════════════════════════════════
    // 内部方法
    // ═══════════════════════════════════════════════════════════════

    private void cancelHandles(TaskHandles handles) {
        if (handles == null) return;
        cancelFuture(handles.dirScan);
        cancelFuture(handles.fileScan);
        cancelFuture(handles.dataPipeline);
    }

    private TaskHandles createHandles(TaskInfo task) {
        ScheduledFuture<?> dirFuture = null;
        ScheduledFuture<?> fileFuture = null;
        ScheduledFuture<?> dataFuture = null;

        if (task.isDirScanEnabled() && task.getDirScanIntervalSec() > 0) {
            dirFuture = scheduler.scheduleWithFixedDelay(
                    () -> safeRun(task.getId(), ScanMode.DIR_SCAN),
                    task.getDirScanIntervalSec(),
                    task.getDirScanIntervalSec(),
                    TimeUnit.SECONDS);
        }

        if (task.isFileScanEnabled() && task.getFileScanIntervalSec() > 0) {
            fileFuture = scheduler.scheduleWithFixedDelay(
                    () -> safeRun(task.getId(), ScanMode.FILE_SCAN),
                    task.getFileScanIntervalSec(),
                    task.getFileScanIntervalSec(),
                    TimeUnit.SECONDS);
        }

        if (task.isDataDriven()) {
            int interval = task.getDirScanIntervalSec() > 0
                    ? task.getDirScanIntervalSec() : 3600;
            dataFuture = scheduler.scheduleWithFixedDelay(
                    () -> safeRun(task.getId(), ScanMode.DATA_PIPELINE),
                    interval, interval, TimeUnit.SECONDS);
        }

        return new TaskHandles(dirFuture, fileFuture, dataFuture);
    }

    private TaskHandles createDataDrivenHandle(TaskInfo task) {
        int interval = task.getDirScanIntervalSec() > 0
                ? task.getDirScanIntervalSec() : 3600;
        ScheduledFuture<?> dataFuture = scheduler.scheduleWithFixedDelay(
                () -> safeRun(task.getId(), ScanMode.DATA_PIPELINE),
                interval, interval, TimeUnit.SECONDS);
        return new TaskHandles(null, null, dataFuture);
    }

    enum ScanMode { DIR_SCAN, FILE_SCAN, DATA_PIPELINE }

    private void safeRun(Long taskId, ScanMode mode) {
        if (pausedTasks.getOrDefault(taskId, false)) {
            return;
        }
        try {
            // 重新加载最新配置
            TaskInfo latest = taskStore.load(taskId);
            if (latest == null || !latest.isEnabled()) return;

            boolean stillEnabled = switch (mode) {
                case DIR_SCAN -> latest.isDirScanEnabled();
                case FILE_SCAN -> latest.isFileScanEnabled();
                case DATA_PIPELINE -> latest.isDataDriven();
            };
            if (!stillEnabled) return;

            switch (mode) {
                case DIR_SCAN -> {
                    ConcurrentHashMap<String, Long> timestamps =
                            dirTimestamps.computeIfAbsent(taskId, k -> new ConcurrentHashMap<>());
                    ScanResult result = runner.runDirScan(latest, timestamps);
                    updateTaskState(taskId, "IDLE", result != null ? "lastRunAt" : null,
                            result != null ? LocalDateTime.now() : null);
                }
                case FILE_SCAN -> {
                    ScanResult result = runner.runFileScan(latest);
                    updateTaskState(taskId, "IDLE", result != null ? "lastRunAt" : null,
                            result != null ? LocalDateTime.now() : null);
                }
                case DATA_PIPELINE -> {
                    eventPublisher.publishEvent(new DataPipelineTriggerEvent(taskId));
                }
            }

        } catch (Exception e) {
            log.error("任务 id={} {} 异常", taskId, mode, e);
            try {
                taskStore.updateState(taskId, "ERROR", LocalDateTime.now(), null, e.getMessage());
            } catch (Exception ignored) { /* best effort */ }
        }
    }

    private void updateTaskState(Long taskId, String state, String field, Object value) {
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime runAt = null;
            if ("lastRunAt".equals(field) && value instanceof LocalDateTime dt) {
                runAt = dt;
            }
            taskStore.updateState(taskId, state, now, runAt, null);
        } catch (Exception e) {
            log.debug("更新任务状态失败: {}", e.getMessage());
        }
    }
}
