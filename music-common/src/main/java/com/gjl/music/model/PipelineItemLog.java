package com.gjl.music.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class PipelineItemLog {
    private Long id;
    private String pipelineId;
    private String itemKey;
    private String status;
    private String errorNode;
    private String errorMsg;
    private LocalDateTime createTime;
}
