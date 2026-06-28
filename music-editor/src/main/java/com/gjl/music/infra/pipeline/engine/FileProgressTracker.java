package com.gjl.music.infra.pipeline.engine;

import com.gjl.music.infra.pipeline.PipelineProgress;
import com.gjl.music.infra.pipeline.PipelineState;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 文件级原子进度追踪器。
 *
 * <p>每完成一个文件立即原子更新，无延迟、无批量跳变。
 * 进度 = (successFiles + failedFiles) / totalFiles。
 */
public class FileProgressTracker {

    private final String pipelineId;
    private final AtomicInteger totalFiles = new AtomicInteger(0);
    private final AtomicInteger successFiles = new AtomicInteger(0);
    private final AtomicInteger failedFiles = new AtomicInteger(0);
    private volatile String errorNode;
    private volatile String errorMessage;

    public FileProgressTracker(String pipelineId) {
        this.pipelineId = pipelineId;
    }

    /** 设置文件总数（在执行开始前调用一次） */
    public void setTotalFiles(int total) {
        totalFiles.set(total);
    }

    /** 增加文件总数 */
    public void addTotalFiles(int delta) {
        totalFiles.addAndGet(delta);
    }

    /** 标记一个文件完成 */
    public void markFileComplete(String fileKey, boolean success, String error) {
        if (success) {
            successFiles.incrementAndGet();
        } else {
            failedFiles.incrementAndGet();
            // 只记录第一个错误
            if (errorNode == null && error != null) {
                this.errorMessage = error;
            }
        }
    }

    /** 设置错误节点名（由 Engine 在最终节点失败时调用） */
    public void setErrorNode(String nodeName) {
        if (this.errorNode == null) {
            this.errorNode = nodeName;
        }
    }

    public void setErrorMessage(String msg) {
        if (this.errorMessage == null) {
            this.errorMessage = msg;
        }
    }

    // ── 查询 ──

    public int getTotalFiles() { return totalFiles.get(); }
    public int getSuccessFiles() { return successFiles.get(); }
    public int getFailedFiles() { return failedFiles.get(); }
    public int getProcessedFiles() { return successFiles.get() + failedFiles.get(); }

    /** 生成当前进度快照，用于 WebSocket 推送 */
    public PipelineProgress snapshot(PipelineState state, String createdAt) {
        return snapshot(state, createdAt, 0);
    }

    /** 生成进度快照，含执行时长 */
    public PipelineProgress snapshot(PipelineState state, String createdAt, long durationMs) {
        return new PipelineProgress(
                pipelineId, state,
                totalFiles.get(), successFiles.get(), failedFiles.get(),
                errorNode, errorMessage, createdAt, durationMs
        );
    }

    @Override
    public String toString() {
        return "Progress{total=" + totalFiles.get()
                + ", success=" + successFiles.get()
                + ", failed=" + failedFiles.get() + '}';
    }
}
