package com.gjl.music.infra.pipeline;

import java.util.Map;
import java.util.concurrent.Executor;

/**
 * 节点执行上下文 —— 节点与框架之间的唯一桥梁。
 *
 * <p>核心能力：
 * <ul>
 *   <li>Slot 读写：节点间通过命名 Slot 交换数据</li>
 *   <li>跨模块调用：{@link #invoke(String, Object)} 运行时同步调用其他已注册模块</li>
 *   <li>生命周期感知：检测取消、阻塞等待暂停恢复</li>
 *   <li>内部并行：通过 {@link #getWorkerExecutor()} 获取线程池</li>
 * </ul>
 */
public interface NodeContext {

    // ── 管道标识 ──

    /** 当前管道唯一标识 */
    String getPipelineId();

    // ── Slot 读写 ──

    @SuppressWarnings("unchecked")
    default <T> T getSlot(String key) {
        return (T) slots().get(key);
    }

    default void setSlot(String key, Object value) {
        slots().put(key, value);
    }

    /** 返回可修改的 Slot Map */
    Map<String, Object> slots();

    // ── 跨模块调用 ──

    /**
     * 运行时同步调用另一个已注册模块。
     * 调用链中不产生日志、不更新进度。
     *
     * @param moduleName 目标模块名（如 "parser", "fingerprint"）
     * @param input      传给目标模块的输入
     * @return 目标模块 NodeResult 的 outputs
     */
    Map<String, Object> invoke(String moduleName, Object input) throws Exception;

    // ── 生命周期 ──

    /** 当前管道是否已被取消 */
    boolean isCancelled();

    /** 当前管道是否正在请求暂停（pausing 或 paused 状态） */
    boolean isPausing();

    /** 阻塞直到暂停被恢复或管道被取消。取消时抛 CancelledException */
    void checkPause() throws CancelledException;

    // ── 内部并行 ──

    /** 获取工作线程池，供节点内部并行处理 */
    Executor getWorkerExecutor();

    // ── 实时进度上报 ──

    /**
     * 实时上报单文件处理结果，驱动前端进度条实时更新。
     *
     * <p>最终节点（如 enrich、batch-write 等批量处理模块）应在每处理完一个文件时
     * 立即调用此方法，使进度可通过 WebSocket 每秒推送到前端。
     * 若不调用，Engine 在节点完成后兜底批量处理，但前端将看不到中间进度。
     *
     * @param itemKey      文件路径标识
     * @param success      是否成功
     * @param errorMessage 错误描述（成功时为 null）
     */
    default void reportItemComplete(String itemKey, boolean success, String errorMessage) {
        // 默认空实现 —— 非最终节点无需逐文件上报
    }

    // ── 异常 ──

    /** 管道被取消时抛出 */
    final class CancelledException extends RuntimeException {
        public CancelledException() {
            super("Pipeline cancelled");
        }
    }
}
