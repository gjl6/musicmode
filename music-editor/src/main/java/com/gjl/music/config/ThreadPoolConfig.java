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


@Slf4j
@Configuration
@EnableAsync
public class ThreadPoolConfig {

    private static final int CPU = Runtime.getRuntime().availableProcessors();
    private static final int MODULE_CORES = Math.max(2, CPU - 2);

    private final ConfigService configService;


    private ThreadPoolTaskExecutor moduleTaskExecutor;


    private volatile Semaphore virtualThrottleSemaphore;

    private ExecutorService rawVirtualExecutor;

    public ThreadPoolConfig(ConfigService configService) {
        this.configService = configService;
    }

    @PostConstruct
    void initVirtualThrottle() {
        int max = configService.getInt("pipeline.executor.virtual.max-concurrent", 0);
        this.virtualThrottleSemaphore = max > 0 ? new Semaphore(max) : null;
    }


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


    @Bean("virtualThreadExecutor")
    public Executor virtualThreadExecutor() {
        ThreadFactory factory = Thread.ofVirtual()
                .name("vt-", 1)
                .uncaughtExceptionHandler((thread, ex) ->
                        log.error("Virtual thread {} terminated by uncaught exception",
                                thread.getName(), ex))
                .factory();
        this.rawVirtualExecutor = Executors.newThreadPerTaskExecutor(factory);

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
