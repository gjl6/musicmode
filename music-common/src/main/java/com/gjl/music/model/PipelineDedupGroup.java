package com.gjl.music.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;


@Getter
@Setter
public class PipelineDedupGroup {
    private Long id;
    private String pipelineId;
    private String strategy;
    private int groupIndex;

    private String filePaths;

    private String fileNames;
    private LocalDateTime createTime;
}
