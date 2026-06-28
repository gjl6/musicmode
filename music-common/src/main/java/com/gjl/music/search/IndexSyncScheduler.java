package com.gjl.music.search;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Lucene 索引定时增量同步 + 启动健康检查。
 *
 * <p>启动检查延迟 30s 执行（等待 ApplicationRunner 数据导入完成），
 * 之后每 10 分钟执行增量同步 + 健康检查。
 */
@Slf4j
@Component
public class IndexSyncScheduler {

    private final IndexManager indexManager;
    private final ScheduledExecutorService startupExecutor = Executors.newSingleThreadScheduledExecutor(
            r -> new Thread(r, "index-startup-check"));

    public IndexSyncScheduler(IndexManager indexManager) {
        this.indexManager = indexManager;
    }

    /**
     * 应用启动就绪后延迟 30s 执行初始检查。
     * 此时 ApplicationRunner（如 AutoTaskScheduler）已完成初始数据导入，
     * 索引构建/健康检查在数据齐备后进行。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        startupExecutor.schedule(() -> {
            try {
                log.info("执行启动后索引检查...");
                indexManager.startupCheck();
            } catch (Exception e) {
                log.error("启动索引检查异常", e);
            }
        }, 30, TimeUnit.SECONDS);
    }

    /**
     * 定期增量同步：先做健康检查（不健康则自动重建），再做增量更新。
     * 间隔由配置 music.search.sync-interval-ms 控制，默认 600000ms (10min)。
     */
    @Scheduled(fixedDelayString = "${music.search.sync-interval-ms:600000}")
    public void syncIndex() {
        try {
            indexManager.healthCheckAndRepair();
            indexManager.syncIncremental();
        } catch (Exception e) {
            log.warn("索引增量同步异常: {}", e.getMessage());
        }
    }
}
