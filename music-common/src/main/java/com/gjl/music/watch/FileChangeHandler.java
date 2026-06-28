package com.gjl.music.watch;

/**
 * 文件变更处理回调 — 扫描器检测到文件变更后调用。
 *
 * <p>实现示例：
 * <ul>
 *   <li>editor: 发布 {@code FileChangesDetectedEvent}，由 {@code WatchRuleService} 监听提交管道</li>
 *   <li>playback: 新文件 → 解析入库；删除文件 → 清理 DB</li>
 * </ul>
 */
@FunctionalInterface
public interface FileChangeHandler {

    /**
     * 处理扫描检测到的文件变更。
     *
     * @param task   当前任务配置
     * @param result 扫描结果（新增/修改/删除的文件列表）
     */
    void onChanges(TaskInfo task, ScanResult result);
}
