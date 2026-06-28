package com.gjl.music.exception;

/**
 * 管道级统一异常 —— 表示管道创建或执行阶段的不可恢复错误。
 *
 * <p>用于配置错误、状态违规、图验证失败等场景，
 * 与 {@link ModuleException}（逐项处理失败）区分语义。</p>
 */
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
