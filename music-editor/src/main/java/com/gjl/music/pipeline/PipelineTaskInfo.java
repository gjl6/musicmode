package com.gjl.music.pipeline;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


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
        info.createdAt = "";
        return info;
    }
}
