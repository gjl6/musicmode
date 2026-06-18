package com.gjl.music.pipeline;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gjl.music.config.ConfigChangedEvent;
import com.gjl.music.config.ConfigService;
import com.gjl.music.mapper.PipelineDedupGroupMapper;
import com.gjl.music.mapper.PipelineItemLogMapper;
import com.gjl.music.mapper.PipelineTaskMapper;
import com.gjl.music.model.PipelineItemLog;
import com.gjl.music.model.PipelineTask;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;


@Slf4j
@Component
public class PipelinePersistence {

    private final PipelineTaskMapper taskMapper;
    private final PipelineItemLogMapper itemLogMapper;
    private final PipelineDedupGroupMapper dedupGroupMapper;
    private final ConfigService configService;
    private final ObjectMapper objectMapper = new ObjectMapper();


    private final ConcurrentLinkedQueue<PipelineItemLog> itemLogBuffer = new ConcurrentLinkedQueue<>();


    private volatile int itemLogFlushSize;

    public PipelinePersistence(PipelineTaskMapper taskMapper,
                               PipelineItemLogMapper itemLogMapper,
                               PipelineDedupGroupMapper dedupGroupMapper,
                               ConfigService configService) {
        this.taskMapper = taskMapper;
        this.itemLogMapper = itemLogMapper;
        this.dedupGroupMapper = dedupGroupMapper;
        this.configService = configService;
    }

    @PostConstruct
    void reloadConfig() {
        this.itemLogFlushSize = configService.getInt("pipeline.persistence.item_log_flush_size", 200);
    }

    @EventListener
    public void onConfigChanged(ConfigChangedEvent e) {
        if ("pipeline.persistence.item_log_flush_size".equals(e.configKey())) {
            this.itemLogFlushSize = e.asInt(200);
        }
    }


    public void insertTask(String id, PipelineState state, String templateName,
                           String graphJson, String[] relativePaths) {
        PipelineTask task = new PipelineTask();
        task.setId(id);
        task.setState(state.name());
        task.setTemplateName(templateName);
        task.setGraphJson(graphJson);
        task.setInputPaths(toJson(relativePaths));
        task.setTotalFiles(0);
        task.setSuccessFiles(0);
        task.setFailedFiles(0);
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.insert(task);
    }

    public void updateState(String id, PipelineState state) {
        taskMapper.updateState(id, state.name());
    }


    public void updateState(String id, PipelineState state, long durationMs) {
        taskMapper.updateStateWithDuration(id, state.name(), durationMs);
    }

    public void updateProgress(String id, int totalFiles, int successFiles,
                                int failedFiles, String errorNode, String errorMessage) {
        taskMapper.updateProgress(id, totalFiles, successFiles, failedFiles,
                errorNode, errorMessage);
    }

    public List<PipelineTask> loadNonTerminal() {
        return taskMapper.selectByNonTerminal();
    }

    public List<PipelineTask> findAllTasks() {
        return taskMapper.selectAll();
    }

    public PipelineTask findTaskById(String id) {
        return taskMapper.selectById(id);
    }


    public void deleteExpiredTasks(java.time.LocalDateTime before) {
        itemLogMapper.deleteExpired(before);
        dedupGroupMapper.deleteExpired(before);
        taskMapper.deleteExpired(before);
    }


    public void bufferItemLog(PipelineItemLog log) {
        itemLogBuffer.add(log);
        if (itemLogBuffer.size() >= itemLogFlushSize) {
            flushItemLogBuffer();
        }
    }


    public void flushItemLogBuffer() {
        List<PipelineItemLog> batch = new ArrayList<>();
        PipelineItemLog item;
        while ((item = itemLogBuffer.poll()) != null) {
            batch.add(item);
        }
        if (!batch.isEmpty()) {
            doBatchInsert(batch);
            log.debug("item log flushed: {} records", batch.size());
        }
    }

    private void doBatchInsert(List<PipelineItemLog> batch) {
        int flushSize = 500;
        for (int i = 0; i < batch.size(); i += flushSize) {
            int end = Math.min(i + flushSize, batch.size());
            itemLogMapper.batchInsert(batch.subList(i, end));
        }
    }

    public List<PipelineItemLog> selectItemLogs(String pipelineId, String status, int page, int size) {
        return itemLogMapper.selectPage(pipelineId, status, page * size, size);
    }

    public int countItemLogs(String pipelineId, String status) {
        return itemLogMapper.countByPipelineStatus(pipelineId, status);
    }

    public Set<String> selectSuccessKeys(String pipelineId) {
        return itemLogMapper.selectSuccessKeys(pipelineId);
    }


    public String toJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); }
        catch (JsonProcessingException e) { log.error("JSON serialization failed", e); return null; }
    }

    public <T> T fromJson(String json, Class<T> clazz) {
        try { return objectMapper.readValue(json, clazz); }
        catch (JsonProcessingException e) { log.error("JSON deserialization failed", e); return null; }
    }

    public <T> T fromJson(String json, TypeReference<T> typeRef) {
        try { return objectMapper.readValue(json, typeRef); }
        catch (JsonProcessingException e) { log.error("JSON deserialization failed", e); return null; }
    }
}
