package com.gjl.music.module;

/**
 * 模块/节点执行失败时的处理策略。
 */
public enum FailurePolicy {

    /** 立即中断整个 Pipeline，后续节点不再执行 */
    FAIL_FAST,

    /** 跳过当前节点，记录错误后继续执行后续节点 */
    SKIP,

    /**
     * 重试当前节点（最多 {@code maxRetries} 次）。
     * 若全部重试仍失败，则降级为 FAIL_FAST 语义。
     */
    RETRY
}
