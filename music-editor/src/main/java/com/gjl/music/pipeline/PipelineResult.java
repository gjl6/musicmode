package com.gjl.music.pipeline;


public class PipelineResult {

    private final String pipelineId;
    private final PipelineState state;
    private final int totalFiles;
    private final int successFiles;
    private final int failedFiles;
    private final String errorNode;
    private final String errorMessage;
    private final long durationMs;

    public PipelineResult(String pipelineId, PipelineState state,
                          int totalFiles, int successFiles, int failedFiles,
                          String errorNode, String errorMessage, long durationMs) {
        this.pipelineId = pipelineId;
        this.state = state;
        this.totalFiles = totalFiles;
        this.successFiles = successFiles;
        this.failedFiles = failedFiles;
        this.errorNode = errorNode;
        this.errorMessage = errorMessage;
        this.durationMs = durationMs;
    }

    public String getPipelineId() { return pipelineId; }
    public PipelineState getState() { return state; }
    public int getTotalFiles() { return totalFiles; }
    public int getSuccessFiles() { return successFiles; }
    public int getFailedFiles() { return failedFiles; }
    public String getErrorNode() { return errorNode; }
    public String getErrorMessage() { return errorMessage; }
    public long getDurationMs() { return durationMs; }

    public boolean isSuccess() {
        return state == PipelineState.COMPLETED;
    }

    @Override
    public String toString() {
        return "PipelineResult{id=" + pipelineId
                + ", state=" + state
                + ", files=" + successFiles + "/" + totalFiles
                + ", failed=" + failedFiles
                + ", duration=" + durationMs + "ms}";
    }
}
