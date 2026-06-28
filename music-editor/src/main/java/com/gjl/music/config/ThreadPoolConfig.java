package com.gjl.music.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 线程池配置 —— 全部通过 ConfigService 参数化，支持热更新。
 *
 * <h3>三个线程池</h3>
 * <ul>
 *   <li><b>pipelineExecutor</b> — 调度线程池，承载管道顶层执行（每个管道占一个线程）</li>
 *   <li><b>moduleParallelExecutor</b> — 工作线程池，模块内部并行处理文件</li>
 *   <li><b>virtualThreadExecutor</b> — 虚拟线程池，I/O 密集型任务（可限流）</li>
 * </ul>
 *
 * <h3>热更新</h3>
 * core/max pool size 通过 {@code ThreadPoolExecutor.setCorePoolSize()/setMaxPoolSize()} 实现；
 * 虚拟线程限流通过替换 {@link Semaphore} 实例实现；
 * queueCapacity 仅启动时生效（{@code ThreadPoolExecutor} 构造限制）。
 */
@Slf4j
@Configuration
@EnableAsync
public class ThreadPoolConfig {

    private static final int CPU = Runtime.getRuntime().availableProcessors();
    private static final int MODULE_CORES = Math.max(2, CPU - 2);

    private final ConfigService configService;

    /** 存储引用，用于热更新 */
    private ThreadPoolTaskExecutor moduleTaskExecutor;

    /** 虚拟线程限流信号量；null 表示不限流 */
    private volatile Semaphore virtualThrottleSemaphore;
    /** 虚拟线程原始 Executor（不限流） */
    private ExecutorService rawVirtualExecutor;

    public ThreadPoolConfig(ConfigService configService) {
        this.configService = configService;
    }

    @PostConstruct
    void initVirtualThrottle() {
        int max = configService.getInt("pipeline.executor.virtual.max-concurrent", 0);
        this.virtualThrottleSemaphore = max > 0 ? new Semaphore(max) : null;
    }

    // ═══════════════════════════════════════════════════════════════
    // 热更新
    // ═══════════════════════════════════════════════════════════════

    @EventListener
    public void onConfigChanged(ConfigChangedEvent e) {
        switch (e.configKey()) {
            case "pipeline.executor.scheduler.pool-size" ->
                    log.info("pipeline.executor.scheduler.pool-size is deprecated (virtual threads used); use max-pipelines instead");
            case "pipeline.executor.worker.core-size" -> {
                if (moduleTaskExecutor != null) {
                    moduleTaskExecutor.setCorePoolSize(e.asInt(MODULE_CORES));
                    log.info("moduleParallelExecutor core-size hot-updated to {}", e.asInt(MODULE_CORES));
                }
            }
            case "pipeline.executor.worker.max-size" -> {
                if (moduleTaskExecutor != null) {
                    moduleTaskExecutor.setMaxPoolSize(e.asInt(CPU));
                    log.info("moduleParallelExecutor max-size hot-updated to {}", e.asInt(CPU));
                }
            }
            case "pipeline.executor.virtual.max-concurrent" -> {
                int max = e.asInt(0);
                this.virtualThrottleSemaphore = max > 0 ? new Semaphore(max) : null;
                log.info("virtualThreadExecutor max-concurrent hot-updated to {}", max);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 线程工厂
    // ═══════════════════════════════════════════════════════════════

    private static ThreadFactory priorityThreadFactory(String prefix, int priority) {
        return new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger();
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, prefix + counter.incrementAndGet());
                t.setPriority(priority);
                t.setUncaughtExceptionHandler((thread, ex) ->
                        log.error("Thread {} terminated by uncaught exception", thread.getName(), ex));
                return t;
            }
        };
    }

    private static org.springframework.core.task.TaskDecorator errorLoggingDecorator(String poolName) {
        return r -> () -> {
            try {
                r.run();
            } catch (Exception e) {
                log.error("{} task error", poolName, e);
                throw e;
            }
        };
    }

    // ═══════════════════════════════════════════════════════════════
    // 前台请求线程池（虚拟线程）
    // ═══════════════════════════════════════════════════════════════

    /** 前台请求线程池 —— 虚拟线程，用于并行 API 调用和文件解析 */
    @Bean("foregroundExecutor")
    public Executor foregroundExecutor() {
        ThreadFactory factory = Thread.ofVirtual()
                .name("fg-", 1)
                .uncaughtExceptionHandler((thread, ex) ->
                        log.error("Virtual thread {} terminated by uncaught exception",
                                thread.getName(), ex))
                .factory();
        ExecutorService raw = Executors.newThreadPerTaskExecutor(factory);
        return runnable -> raw.execute(() -> {
            try {
                runnable.run();
            } catch (Exception e) {
                log.error("foregroundExecutor task error", e);
                throw e;
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════
    // 管道调度线程池 —— 虚拟线程 + Semaphore 限流
    // ═══════════════════════════════════════════════════════════════

    /**
     * 管道调度执行器 —— 虚拟线程，每个管道独占一个虚拟线程阻塞在 engine.execute()。
     *
     * <p>虚拟线程阻塞时自动 unmount carrier thread，不消耗平台线程资源。
     * 并发数由 {@code PipelineOrchestrator} 中的 Semaphore 控制。
     *
     * <p>{@code pipeline.executor.scheduler.pool-size} 已废弃（虚拟线程无池）。
     */
    @Bean("pipelineExecutor")
    public Executor pipelineExecutor() {
        ThreadFactory factory = Thread.ofVirtual()
                .name("pipeline-", 1)
                .uncaughtExceptionHandler((thread, ex) ->
                        log.error("Virtual thread {} terminated by uncaught exception",
                                thread.getName(), ex))
                .factory();
        ExecutorService raw = Executors.newThreadPerTaskExecutor(factory);
        return runnable -> raw.execute(() -> {
            try {
                runnable.run();
            } catch (Exception e) {
                log.error("pipelineExecutor task error", e);
                throw e;
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════
    // 模块工作线程池 ★ 核心
    // ═══════════════════════════════════════════════════════════════

    /**
     * 模块工作线程池 —— 管道内模块并行处理文件。
     */
    @Bean("moduleParallelExecutor")
    public Executor moduleParallelExecutor() {
        int coreSize = configService.getInt("pipeline.executor.worker.core-size", MODULE_CORES);
        int maxSize = configService.getInt("pipeline.executor.worker.max-size", CPU);
        int queueCap = configService.getInt("pipeline.executor.worker.queue-capacity", 2000);
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCap);
        executor.setThreadFactory(priorityThreadFactory("worker-", Thread.NORM_PRIORITY - 1));
        executor.setRejectedExecutionHandler(
                new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.setTaskDecorator(errorLoggingDecorator("moduleParallelExecutor"));
        executor.initialize();
        this.moduleTaskExecutor = executor;
        log.info("moduleParallelExecutor initialized: core={}, max={}, queue={}",
                coreSize, maxSize, queueCap);
        return executor;
    }

    // ═══════════════════════════════════════════════════════════════
    // 虚拟线程池（可限流）
    // ═══════════════════════════════════════════════════════════════

    /**
     * 虚拟线程 Executor —— 模块内部 I/O 密集型并行任务。
     *
     * <p>通过 pipeline.executor.virtual.max-concurrent 限制最大并发虚拟线程数。
     * 设置为 0 时不限流。热更新时替换 Semaphore 实例，旧任务继续使用旧 Semaphore。
     */
    @Bean("virtualThreadExecutor")
    public Executor virtualThreadExecutor() {
        ThreadFactory factory = Thread.ofVirtual()
                .name("vt-", 1)
                .uncaughtExceptionHandler((thread, ex) ->
                        log.error("Virtual thread {} terminated by uncaught exception",
                                thread.getName(), ex))
                .factory();
        this.rawVirtualExecutor = Executors.newThreadPerTaskExecutor(factory);

        // 返回限流包装
        return runnable -> {
            Semaphore sem = virtualThrottleSemaphore;
            if (sem == null) {
                rawVirtualExecutor.execute(() -> runSafely(runnable));
            } else {
                rawVirtualExecutor.execute(() -> {
                    try {
                        sem.acquire();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    try {
                        runSafely(runnable);
                    } finally {
                        sem.release();
                    }
                });
            }
        };
    }

    private void runSafely(Runnable runnable) {
        try {
            runnable.run();
        } catch (Exception e) {
            log.error("virtualThreadExecutor task error", e);
            throw e;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 共享调度器
    // ═══════════════════════════════════════════════════════════════

    /** 共享调度线程池 —— CheckpointBuffer 定时刷盘复用 */
    @Bean("sharedScheduler")
    public TaskScheduler sharedScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("sched-");
        scheduler.setTaskDecorator(errorLoggingDecorator("sharedScheduler"));
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.initialize();
        return scheduler;
    }
}
