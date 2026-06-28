package com.gjl.music.watch;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务持久化接口 — 调度器通过此接口加载/更新任务配置和状态。
 *
 * <p>实现示例：
 * <ul>
 *   <li>editor: {@code WatchRuleMapper} 适配（DB 持久化）</li>
 *   <li>playback: 代码中写死的固定任务列表（无需 DB）</li>
 * </ul>
 */
public interface TaskStore {

    /** 启动时加载所有已启用的任务 */
    List<? extends TaskInfo> loadAll();

    /** 执行前重新加载最新配置（可能已被用户修改） */
    TaskInfo load(Long id);

    /** 轻量写入运行状态（不覆盖配置字段） */
    void updateState(Long id, String state, LocalDateTime scanAt,
                     LocalDateTime runAt, String error);
}
