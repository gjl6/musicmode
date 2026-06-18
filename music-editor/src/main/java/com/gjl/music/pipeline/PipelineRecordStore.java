package com.gjl.music.pipeline;

import com.gjl.music.config.ConfigChangedEvent;
import com.gjl.music.config.ConfigService;
import com.gjl.music.model.PipelineTask;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;


@Slf4j
@Component
public class PipelineRecordStore {

    private static final String ZSET_ACTIVE = "pipeline:active";
    private static final String HASH_PREFIX = "pipeline:task:";

    private static final String EMPTY_MARKER = "__EMPTY__";
    private static final Set<String> TERMINAL_STATES = Set.of("COMPLETED", "FAILED", "CANCELLED");

    private final StringRedisTemplate redis;
    private final PipelinePersistence db;
    private final ConfigService configService;
    private final boolean redisEnabled;


    private volatile int maxActiveSize;

    private volatile Duration emptyTtl;

    public PipelineRecordStore(
            @Autowired(required = false) StringRedisTemplate stringRedisTemplate,
            PipelinePersistence db,
            ConfigService configService) {
        this.redis = stringRedisTemplate;
        this.db = db;
        this.configService = configService;
        this.redisEnabled = stringRedisTemplate != null;
    }

    @PostConstruct
    void reloadConfig() {
        this.maxActiveSize = configService.getInt("pipeline.store.max_active_size", 100);
        this.emptyTtl = Duration.ofMinutes(configService.getInt("pipeline.store.cache_ttl_minutes", 5));
    }

    @EventListener
    public void onConfigChanged(ConfigChangedEvent e) {
        switch (e.configKey()) {
            case "pipeline.store.max_active_size" -> this.maxActiveSize = e.asInt(100);
            case "pipeline.store.cache_ttl_minutes" -> this.emptyTtl = Duration.ofMinutes(e.asInt(5));
        }
    }


    public void save(PipelineTaskInfo info) {
        if (redisEnabled) {
            try {
                String key = hashKey(info.getPipelineId());
                redis.opsForHash().putAll(key, toHashFields(info));
                long score = toEpochMillis(info.getCreatedAt());
                redis.opsForZSet().add(ZSET_ACTIVE, info.getPipelineId(), score);
                                redis.opsForZSet().removeRange(ZSET_ACTIVE, 0, -(maxActiveSize + 1));
            } catch (Exception e) {
                log.warn("Redis 写入失败: {}", e.getMessage());
            }
        }
    }


    public void updateState(String pipelineId, PipelineState state) {
        if (redisEnabled) {
            try {
                if (TERMINAL_STATES.contains(state.name())) {
                                        redis.opsForZSet().remove(ZSET_ACTIVE, pipelineId);
                    redis.delete(hashKey(pipelineId));
                } else {
                    redis.opsForHash().put(hashKey(pipelineId), "state", state.name());
                }
            } catch (Exception e) {
                log.debug("Redis 更新状态失败（忽略）: {}", e.getMessage());
            }
        }
    }


    public void updateProgress(String pipelineId, int totalFiles, int successFiles,
                                int failedFiles, String errorNode, String errorMessage) {
        if (redisEnabled) {
            try {
                String key = hashKey(pipelineId);
                if (Boolean.TRUE.equals(redis.hasKey(key))) {
                    Map<String, String> updates = new LinkedHashMap<>();
                    updates.put("totalFiles", String.valueOf(totalFiles));
                    updates.put("successFiles", String.valueOf(successFiles));
                    updates.put("failedFiles", String.valueOf(failedFiles));
                    if (errorNode != null) updates.put("errorModule", errorNode);
                    if (errorMessage != null) updates.put("errorMessage", errorMessage);
                    redis.opsForHash().putAll(key, updates);
                }
            } catch (Exception e) {
                log.debug("Redis 更新进度失败（忽略）: {}", e.getMessage());
            }
        }
    }


    public List<PipelineTaskInfo> listAll() {
                List<PipelineTask> allTasks = db.findAllTasks();
        if (allTasks == null || allTasks.isEmpty()) {
            cacheEmptyMarker();
            return List.of();
        }

        List<PipelineTaskInfo> result = new ArrayList<>(allTasks.size());
        for (PipelineTask task : allTasks) {
            result.add(PipelineTaskInfo.from(task));
        }

                if (redisEnabled) {
            try {
                Map<String, Map<Object, Object>> activeSnapshots = readActiveFromRedisAsMap();
                for (PipelineTaskInfo info : result) {
                    Map<Object, Object> live = activeSnapshots.get(info.getPipelineId());
                    if (live != null && !live.isEmpty()) {
                        overlay(info, live);
                    }
                }
            } catch (Exception e) {
                log.warn("Redis 活跃覆盖失败，使用 DB 数据: {}", e.getMessage());
            }
        }

        return result;
    }


    public PipelineTaskInfo findById(String pipelineId) {
        if (redisEnabled) {
            try {
                Map<Object, Object> fields = redis.opsForHash().entries(hashKey(pipelineId));
                if (fields != null && !fields.isEmpty()) {
                    return mapToInfo(fields);
                }
            } catch (Exception e) {
                log.debug("Redis 查询 {} 失败: {}", pipelineId, e.getMessage());
            }
        }
        PipelineTask task = db.findTaskById(pipelineId);
        return task != null ? PipelineTaskInfo.from(task) : null;
    }


    public List<PipelineTaskInfo> loadNonTerminal() {
        if (redisEnabled) {
            try {
                List<PipelineTaskInfo> active = readActiveAsList();
                if (!active.isEmpty()) {
                    return active.stream()
                            .filter(t -> !TERMINAL_STATES.contains(t.getState()))
                            .collect(Collectors.toList());
                }
            } catch (Exception e) {
                log.warn("Redis 恢复查询失败，穿透 DB: {}", e.getMessage());
            }
        }
        List<PipelineTask> tasks = db.loadNonTerminal();
        return tasks.stream().map(PipelineTaskInfo::from).collect(Collectors.toList());
    }


    public void cleanupExpired(LocalDateTime horizon) {
                try {
            db.deleteExpiredTasks(horizon);
        } catch (Exception e) {
            log.warn("DB 清理过期记录失败: {}", e.getMessage());
        }

                if (redisEnabled) {
            long horizonMillis = toEpochMillis(horizon);
            try {
                Set<String> oldIds = redis.opsForZSet()
                        .rangeByScore(ZSET_ACTIVE, 0, horizonMillis);
                if (oldIds != null && !oldIds.isEmpty()) {
                    for (String id : oldIds) {
                        String state = (String) redis.opsForHash().get(hashKey(id), "state");
                        if (state != null && TERMINAL_STATES.contains(state)) {
                            redis.opsForZSet().remove(ZSET_ACTIVE, id);
                            redis.delete(hashKey(id));
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("Redis 清理过期失败（忽略）: {}", e.getMessage());
            }
        }
    }


    private Map<String, Map<Object, Object>> readActiveFromRedisAsMap() {
        Set<String> ids = redis.opsForZSet().reverseRange(ZSET_ACTIVE, 0, -1);
        if (ids == null || ids.isEmpty()) return Map.of();

        Map<String, Map<Object, Object>> result = new LinkedHashMap<>();
        for (String id : ids) {
            Map<Object, Object> fields = redis.opsForHash().entries(hashKey(id));
            if (fields != null && !fields.isEmpty()) {
                result.put(id, fields);
            }
        }
        return result;
    }


    private List<PipelineTaskInfo> readActiveAsList() {
        Set<String> ids = redis.opsForZSet().reverseRange(ZSET_ACTIVE, 0, -1);
        if (ids == null || ids.isEmpty()) return List.of();

        List<PipelineTaskInfo> result = new ArrayList<>();
        for (String id : ids) {
            Map<Object, Object> fields = redis.opsForHash().entries(hashKey(id));
            if (fields != null && !fields.isEmpty()) {
                result.add(mapToInfo(fields));
            }
        }
        return result;
    }


    private static void overlay(PipelineTaskInfo info, Map<Object, Object> live) {
        info.setState(str(live, "state"));
        info.setTotalFiles(integer(live, "totalFiles"));
        info.setSuccessFiles(integer(live, "successFiles"));
        info.setFailedFiles(integer(live, "failedFiles"));
        info.setErrorModule(str(live, "errorModule"));
        info.setErrorMessage(str(live, "errorMessage"));
        info.setInputPaths(str(live, "inputPaths"));
    }


    private void cacheEmptyMarker() {
        if (redisEnabled) {
            try {
                redis.opsForValue().set(emptyKey(), EMPTY_MARKER, emptyTtl);
            } catch (Exception e) {
                log.debug("缓存空标识失败（忽略）: {}", e.getMessage());
            }
        }
    }

    private static Map<String, String> toHashFields(PipelineTaskInfo info) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("pipelineId", info.getPipelineId());
        fields.put("templateName", info.getTemplateName() != null ? info.getTemplateName() : "");
        fields.put("state", info.getState());
        fields.put("totalFiles", String.valueOf(info.getTotalFiles()));
        fields.put("successFiles", String.valueOf(info.getSuccessFiles()));
        fields.put("failedFiles", String.valueOf(info.getFailedFiles()));
        fields.put("errorModule", info.getErrorModule() != null ? info.getErrorModule() : "");
        fields.put("errorMessage", info.getErrorMessage() != null ? info.getErrorMessage() : "");
        fields.put("inputPaths", info.getInputPaths() != null ? info.getInputPaths() : "");
        fields.put("createdAt", info.getCreatedAt() != null ? info.getCreatedAt() : "");
        return fields;
    }

    private static String hashKey(String pipelineId) {
        return HASH_PREFIX + pipelineId;
    }

    private static String emptyKey() {
        return HASH_PREFIX + "empty";
    }

    private static long toEpochMillis(String createdAt) {
        if (createdAt == null || createdAt.isEmpty()) return 0;
        return toEpochMillis(parseDateTime(createdAt));
    }

    private static long toEpochMillis(LocalDateTime dt) {
        return dt.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static LocalDateTime parseDateTime(String s) {
        try {
            String n = s.replace("T", " ");
            if (n.length() > 19) n = n.substring(0, 19);
            return LocalDateTime.parse(n, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception e) {
            return LocalDateTime.of(1970, 1, 1, 0, 0);
        }
    }

    private PipelineTaskInfo mapToInfo(Map<Object, Object> fields) {
        PipelineTaskInfo info = new PipelineTaskInfo();
        info.setPipelineId(str(fields, "pipelineId"));
        info.setTemplateName(str(fields, "templateName"));
        info.setState(str(fields, "state"));
        info.setTotalFiles(integer(fields, "totalFiles"));
        info.setSuccessFiles(integer(fields, "successFiles"));
        info.setFailedFiles(integer(fields, "failedFiles"));
        info.setErrorModule(str(fields, "errorModule"));
        info.setErrorMessage(str(fields, "errorMessage"));
        info.setInputPaths(str(fields, "inputPaths"));
        info.setCreatedAt(str(fields, "createdAt"));
        return info;
    }

    private static String str(Map<Object, Object> f, String k) {
        Object v = f.get(k);
        return v != null ? v.toString() : "";
    }

    private static int integer(Map<Object, Object> f, String k) {
        Object v = f.get(k);
        if (v == null) return 0;
        try { return Integer.parseInt(v.toString()); }
        catch (NumberFormatException e) { return 0; }
    }
}
