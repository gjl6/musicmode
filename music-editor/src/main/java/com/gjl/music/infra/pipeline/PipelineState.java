package com.gjl.music.infra.pipeline;

/**
 * Pipeline 生命周期状态。
 * READY → RUNNING → COMPLETED / FAILED / CANCELLED
 *               ↕
 *             PAUSED
 */
public enum PipelineState {
    READY,
    RUNNING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}
