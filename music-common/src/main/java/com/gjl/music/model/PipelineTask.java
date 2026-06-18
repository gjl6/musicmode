package com.gjl.music.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class PipelineTask {
    private String id;
    private String state;
    private String templateName;
    private String graphJson;
    private String inputPaths;
    private Integer totalFiles;
    private Integer successFiles;
    private Integer failedFiles;
    private String errorNode;
    private String errorMessage;
    private Long durationMs;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
