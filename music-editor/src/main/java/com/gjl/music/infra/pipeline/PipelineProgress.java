package com.gjl.music.infra.pipeline;

/**
 * WebSocket 进度推送 DTO —— 极简，仅含前端需要的字段。
 */
public class PipelineProgress {

    private final String pipelineId;
    private final PipelineState state;
    private final int totalFiles;
    private final int successFiles;
    private final int failedFiles;
    private final String errorNode;
    private final String errorMessage;
    private final long timestamp;
    private final String createdAt;
    private final long durationMs;

    public PipelineProgress(String pipelineId, PipelineState state,
                            int totalFiles, int successFiles, int failedFiles,
                            String errorNode, String errorMessage,
                            String createdAt) {
        this(pipelineId, state, totalFiles, successFiles, failedFiles,
                errorNode, errorMessage, createdAt, 0);
    }

    public PipelineProgress(String pipelineId, PipelineState state,
                            int totalFiles, int successFiles, int failedFiles,
                            String errorNode, String errorMessage,
                            String createdAt, long durationMs) {
        this.pipelineId = pipelineId;
        this.state = state;
        this.totalFiles = totalFiles;
        this.successFiles = successFiles;
        this.failedFiles = failedFiles;
        this.errorNode = errorNode;
        this.errorMessage = errorMessage;
        this.timestamp = System.currentTimeMillis();
        this.createdAt = createdAt;
        this.durationMs = durationMs;
    }

    public String getPipelineId() { return pipelineId; }
    public PipelineState getState() { return state; }
    public int getTotalFiles() { return totalFiles; }
    public int getSuccessFiles() { return successFiles; }
    public int getFailedFiles() { return failedFiles; }
    public String getErrorNode() { return errorNode; }
    public String getErrorMessage() { return errorMessage; }
    public long getTimestamp() { return timestamp; }
    public String getCreatedAt() { return createdAt; }
    public long getDurationMs() { return durationMs; }
}
