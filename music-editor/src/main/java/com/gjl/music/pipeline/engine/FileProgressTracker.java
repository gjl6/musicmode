package com.gjl.music.pipeline.engine;

import com.gjl.music.pipeline.PipelineProgress;
import com.gjl.music.pipeline.PipelineState;

import java.util.concurrent.atomic.AtomicInteger;


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


    public void setTotalFiles(int total) {
        totalFiles.set(total);
    }


    public void addTotalFiles(int delta) {
        totalFiles.addAndGet(delta);
    }


    public void markFileComplete(String fileKey, boolean success, String error) {
        if (success) {
            successFiles.incrementAndGet();
        } else {
            failedFiles.incrementAndGet();
                        if (errorNode == null && error != null) {
                this.errorMessage = error;
            }
        }
    }


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


    public int getTotalFiles() { return totalFiles.get(); }
    public int getSuccessFiles() { return successFiles.get(); }
    public int getFailedFiles() { return failedFiles.get(); }
    public int getProcessedFiles() { return successFiles.get() + failedFiles.get(); }


    public PipelineProgress snapshot(PipelineState state, String createdAt) {
        return snapshot(state, createdAt, 0);
    }


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
