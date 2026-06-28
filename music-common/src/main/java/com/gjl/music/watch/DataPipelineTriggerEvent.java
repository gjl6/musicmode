package com.gjl.music.watch;

/**
 * 数据驱动管道触发事件 — 由 {@link AutoTaskScheduler} 发布，
 * 由 {@code WatchRuleService} 监听处理。
 *
 * <p>用于没有文件扫描、定期执行管道的任务（如 artist-enrich / album-normalize）。
 * 位于 common 以便调度器和监听器在不同模块中。
 */
public record DataPipelineTriggerEvent(Long taskId) {
}
