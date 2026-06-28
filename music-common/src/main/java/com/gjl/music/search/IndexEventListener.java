package com.gjl.music.search;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/**
 * 索引事件监听器 — 消费 {@link EntityChangeEvent}，驱动 Lucene 索引实时增删改。
 *
 * <p>设计要点：
 * <ul>
 *   <li>事件先入队（非阻塞），后台虚拟线程批量消费，避免阻塞 Pipeline/HTTP 线程</li>
 *   <li>200ms 批处理窗口：聚合同一批次内的多次变更，单次 commit，减少索引写入开销</li>
 *   <li>索引更新失败不抛异常——记录日志后继续，保证搜索降级而非主业务中断</li>
 * </ul>
 */
@Slf4j
@Component
public class IndexEventListener {

    private final IndexManager indexManager;
    private final BlockingQueue<EntityChangeEvent> queue = new LinkedBlockingQueue<>(10000);
    private volatile boolean running = true;
    private Thread processorThread;

    public IndexEventListener(IndexManager indexManager) {
        this.indexManager = indexManager;
    }

    @PostConstruct
    void start() {
        processorThread = Thread.ofVirtual()
                .name("index-event-processor")
                .start(this::processLoop);
        log.info("索引事件处理器已启动");
    }

    @PreDestroy
    void stop() {
        running = false;
    }

    /**
     * 接收实体变更事件（同步返回，不入队阻塞调用方）。
     */
    @EventListener
    public void onEntityChange(EntityChangeEvent event) {
        if (event == null || event.entityId() == null) return;
        // offer 非阻塞——队列满时丢弃并告警（10K 容量在正常场景下不会满）
        if (!queue.offer(event)) {
            log.warn("索引事件队列已满(10000)，丢弃事件: {}", event);
        }
    }

    // ── 后台处理循环 ──

    private void processLoop() {
        List<EntityChangeEvent> batch = new ArrayList<>(512);
        while (running) {
            try {
                // 等待第一个事件，最多 200ms（防止空转）
                EntityChangeEvent first = queue.poll(200, TimeUnit.MILLISECONDS);
                if (first != null) {
                    batch.add(first);
                    // 尽可能多地排空队列
                    queue.drainTo(batch, 500);
                    processBatch(batch);
                    batch.clear();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.warn("索引事件批处理异常: {}", e.getMessage(), e);
                batch.clear();
            }
        }
        // 关闭前排空剩余事件
        List<EntityChangeEvent> remaining = new ArrayList<>();
        queue.drainTo(remaining);
        if (!remaining.isEmpty()) {
            try { processBatch(remaining); } catch (Exception ignored) { }
        }
    }

    // ── 批量处理 ──

    private void processBatch(List<EntityChangeEvent> events) {
        // 去重：同一实体多次变更只保留最后一次
        Map<String, EntityChangeEvent> deduped = new LinkedHashMap<>();
        for (EntityChangeEvent e : events) {
            String key = e.entityType() + ":" + e.entityId();
            EntityChangeEvent existing = deduped.get(key);
            // DELETED 优先级最高，UPDATED 覆盖 CREATED
            if (existing == null || e.changeType() == EntityChangeEvent.ChangeType.DELETED) {
                deduped.put(key, e);
            } else if (e.changeType() == EntityChangeEvent.ChangeType.UPDATED
                    && existing.changeType() == EntityChangeEvent.ChangeType.CREATED) {
                deduped.put(key, e);
            }
        }

        int indexed = 0, deleted = 0, errors = 0;
        for (EntityChangeEvent e : deduped.values()) {
            try {
                switch (e.changeType()) {
                    case CREATED, UPDATED -> {
                        indexEntity(e);
                        indexed++;
                    }
                    case DELETED -> {
                        deleteEntity(e);
                        deleted++;
                    }
                }
            } catch (Exception ex) {
                errors++;
                log.warn("索引事件处理失败: entityType={}, id={}, changeType={}",
                        e.entityType(), e.entityId(), e.changeType(), ex);
            }
        }

        // 统一 commit
        try {
            indexManager.commit();
        } catch (IOException ex) {
            log.warn("索引 commit 失败: {}", ex.getMessage());
        }

        if (indexed + deleted > 0) {
            log.debug("索引事件批处理完成: indexed={}, deleted={}, errors={}, totalEvents={}",
                    indexed, deleted, errors, events.size());
        }
    }

    private void indexEntity(EntityChangeEvent e) throws IOException {
        switch (e.entityType()) {
            case SONG -> {
                // 歌曲需要查最新数据再索引（genre/lyrics/artists 信息）
                indexManager.reindexSong(e.entityId());
            }
            case ALBUM -> indexManager.reindexAlbum(e.entityId());
            case ARTIST -> indexManager.reindexArtist(e.entityId());
        }
    }

    private void deleteEntity(EntityChangeEvent e) throws IOException {
        switch (e.entityType()) {
            case SONG -> indexManager.deleteSong(e.entityId());
            case ALBUM -> indexManager.deleteAlbum(e.entityId());
            case ARTIST -> indexManager.deleteArtist(e.entityId());
        }
    }
}
