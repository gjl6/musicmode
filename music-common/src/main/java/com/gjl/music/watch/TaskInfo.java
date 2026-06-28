package com.gjl.music.watch;

/**
 * 自动任务配置（只读快照），由 {@link AutoTaskScheduler} 在每次执行前从 {@link TaskStore} 获取。
 *
 * <p>实现类可以是 DB 实体（如 {@code WatchRule}）或代码中定义的固定任务。
 */
public interface TaskInfo {

    /** 任务唯一标识 */
    Long getId();

    /** 任务名称（日志/UI 显示） */
    String getName();

    /** 监控子路径（{@code "/"} = 根目录，{@code null/blank} = 不扫描文件） */
    String getWatchPath();

    /** 任务是否启用 */
    boolean isEnabled();

    /** 是否启用目录级扫描 */
    boolean isDirScanEnabled();

    /** 目录扫描间隔（秒） */
    int getDirScanIntervalSec();

    /** 是否启用文件级扫描 */
    boolean isFileScanEnabled();

    /** 文件扫描间隔（秒） */
    int getFileScanIntervalSec();

    /** 是否有任何扫描已启用 */
    default boolean isAnyScanEnabled() {
        return isEnabled() && (isDirScanEnabled() || isFileScanEnabled());
    }

    /** 是否配置了管道处理步骤（非空 steps_json） */
    default boolean hasPipelineSteps() {
        return false;
    }

    /** 是否纯数据驱动（有步骤但无文件扫描） */
    default boolean isDataDriven() {
        return isEnabled() && !isAnyScanEnabled() && hasPipelineSteps();
    }
}
