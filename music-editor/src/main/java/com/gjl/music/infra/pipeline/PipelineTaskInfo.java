package com.gjl.music.infra.pipeline;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 管道任务记录 DTO —— Redis Hash 与 API 响应之间的统一载体。
 *
 * <p>替代原始的 {@code Map<String, Object>}，字段类型明确，
 * Jackson 序列化后前端可直接消费。</p>
 */
@Getter
@Setter
@NoArgsConstructor
public class PipelineTaskInfo {

    private String pipelineId;
    private String templateName;
    private String state;
    private int totalFiles;
    private int successFiles;
    private int failedFiles;
    private String errorModule;
    private String errorMessage;
    private String inputPaths;
    private String createdAt;

    /** 从 PipelineTask 模型转换 */
    public static PipelineTaskInfo from(com.gjl.music.model.PipelineTask task) {
        PipelineTaskInfo info = new PipelineTaskInfo();
        info.pipelineId = task.getId();
        info.templateName = task.getTemplateName() != null ? task.getTemplateName() : "";
        info.state = task.getState();
        info.inputPaths = task.getInputPaths() != null ? task.getInputPaths() : "";
        info.totalFiles = task.getTotalFiles() != null ? task.getTotalFiles() : 0;
        info.successFiles = task.getSuccessFiles() != null ? task.getSuccessFiles() : 0;
        info.failedFiles = task.getFailedFiles() != null ? task.getFailedFiles() : 0;
        info.errorModule = task.getErrorNode() != null ? task.getErrorNode() : "";
        info.errorMessage = task.getErrorMessage() != null ? task.getErrorMessage() : "";
        info.createdAt = task.getCreateTime() != null ? task.getCreateTime().toString() : "";
        return info;
    }

    /** 从 PipelineProgress + state 构建（仅含运行中字段） */
    public static PipelineTaskInfo from(String pipelineId, String templateName,
                                         PipelineState state, PipelineProgress progress) {
        PipelineTaskInfo info = new PipelineTaskInfo();
        info.pipelineId = pipelineId;
        info.templateName = templateName != null ? templateName : "";
        info.state = state.name();
        info.totalFiles = progress.getTotalFiles();
        info.successFiles = progress.getSuccessFiles();
        info.failedFiles = progress.getFailedFiles();
        info.errorModule = progress.getErrorNode() != null ? progress.getErrorNode() : "";
        info.errorMessage = progress.getErrorMessage() != null ? progress.getErrorMessage() : "";
        info.inputPaths = "";
        info.createdAt = ""; // caller 应该设置
        return info;
    }
}
