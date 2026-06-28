package com.gjl.music.model;

import com.gjl.music.watch.TaskInfo;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 自动任务规则 —— 独立调度 + 两级扫描 + 处理步骤链。
 *
 * <p>每条任务定义：监控子目录、DIR_SCAN/FILE_SCAN 各自的调度间隔、处理步骤链。
 * <p>v2 重构：从全局监控+规则匹配改为每任务独立调度扫描。
 */
public class WatchRule implements TaskInfo {

    private Long id;
    private String name;
    private String watchPath;
    private boolean enabled = true;

    /** @deprecated v2 废弃，被 {@link #dirScanEnabled}/{@link #fileScanEnabled} 取代 */
    @Deprecated
    private boolean autoTrigger = true;

    private String stepsJson;
    private int priority;
    private String description;

    // ── v2: 两级扫描调度 ──

    /** 启用目录级扫描（检查目录 mtime，跳过未变子树） */
    private boolean dirScanEnabled = true;
    /** 目录扫描间隔（秒） */
    private int dirScanIntervalSec = 60;
    /** 启用文件级扫描（逐文件对比 mtime 与 song 表） */
    private boolean fileScanEnabled = true;
    /** 文件扫描间隔（秒） */
    private int fileScanIntervalSec = 3600;

    // ── v2: 运行状态 ──

    /** 当前状态：IDLE / WAITING / RUNNING / ERROR */
    private String state = "IDLE";
    /** 上次扫描时间 */
    private LocalDateTime lastScanAt;
    /** 上次管道提交时间 */
    private LocalDateTime lastRunAt;
    /** 最后一次错误信息 */
    private String errorMessage;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    // ── 非持久化字段（由 Service 层解析）──

    /** steps_json 解析后的步骤列表 */
    private List<WatchStep> steps = Collections.emptyList();

    // ═══════════════════════════════════════════════════════════════
    // Getters / Setters
    // ═══════════════════════════════════════════════════════════════

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getWatchPath() { return watchPath; }
    public void setWatchPath(String watchPath) { this.watchPath = watchPath; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    /** @deprecated v2 废弃 */
    @Deprecated
    public boolean isAutoTrigger() { return autoTrigger; }
    /** @deprecated v2 废弃 */
    @Deprecated
    public void setAutoTrigger(boolean autoTrigger) { this.autoTrigger = autoTrigger; }

    public String getStepsJson() { return stepsJson; }
    public void setStepsJson(String stepsJson) { this.stepsJson = stepsJson; }

    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    // ── v2: 扫描配置 ──

    public boolean isDirScanEnabled() { return dirScanEnabled; }
    public void setDirScanEnabled(boolean dirScanEnabled) { this.dirScanEnabled = dirScanEnabled; }

    public int getDirScanIntervalSec() { return dirScanIntervalSec; }
    public void setDirScanIntervalSec(int dirScanIntervalSec) { this.dirScanIntervalSec = dirScanIntervalSec; }

    public boolean isFileScanEnabled() { return fileScanEnabled; }
    public void setFileScanEnabled(boolean fileScanEnabled) { this.fileScanEnabled = fileScanEnabled; }

    public int getFileScanIntervalSec() { return fileScanIntervalSec; }
    public void setFileScanIntervalSec(int fileScanIntervalSec) { this.fileScanIntervalSec = fileScanIntervalSec; }

    // ── v2: 运行状态 ──

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public LocalDateTime getLastScanAt() { return lastScanAt; }
    public void setLastScanAt(LocalDateTime lastScanAt) { this.lastScanAt = lastScanAt; }

    public LocalDateTime getLastRunAt() { return lastRunAt; }
    public void setLastRunAt(LocalDateTime lastRunAt) { this.lastRunAt = lastRunAt; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    // ── 时间戳 ──

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    // ── 非持久化 ──

    public List<WatchStep> getSteps() { return steps; }
    public void setSteps(List<WatchStep> steps) { this.steps = steps != null ? steps : Collections.emptyList(); }

    /** 是否有任何扫描启用 */
    public boolean isAnyScanEnabled() {
        return enabled && (dirScanEnabled || fileScanEnabled);
    }

    /** 检查 steps_json 是否有实际内容 */
    @Override
    public boolean hasPipelineSteps() {
        String json = getStepsJson();
        return json != null && !json.isBlank() && !"[]".equals(json.trim());
    }

    @Override
    public String toString() {
        return "WatchRule{id=" + id + ", name=" + name + ", watchPath=" + watchPath
                + ", enabled=" + enabled + ", dirScan=" + dirScanEnabled + "/" + dirScanIntervalSec + "s"
                + ", fileScan=" + fileScanEnabled + "/" + fileScanIntervalSec + "s"
                + ", state=" + state + ", steps=" + steps.size() + '}';
    }
}
