package com.gjl.music.exception;


public class PipelineException extends RuntimeException {

    private final String pipelineId;
    private String errorCode;

    public PipelineException(String message) {
        super(message);
        this.pipelineId = null;
    }

    public PipelineException(String message, Throwable cause) {
        super(message, cause);
        this.pipelineId = null;
    }

    public PipelineException(String pipelineId, String message) {
        super(message);
        this.pipelineId = pipelineId;
    }

    public PipelineException(String pipelineId, String message, Throwable cause) {
        super(message, cause);
        this.pipelineId = pipelineId;
    }

    public PipelineException withErrorCode(String errorCode) {
        this.errorCode = errorCode;
        return this;
    }

    public String getPipelineId() { return pipelineId; }
    public String getErrorCode() { return errorCode; }
}
