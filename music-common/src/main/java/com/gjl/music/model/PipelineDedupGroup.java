package com.gjl.music.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 去重分组持久化模型 —— 每行一个重复组 */
@Getter
@Setter
public class PipelineDedupGroup {
    private Long id;
    private String pipelineId;
    private String strategy;
    private int groupIndex;
    /** JSON 数组：完整文件路径列表 */
    private String filePaths;
    /** JSON 数组：文件名列表 */
    private String fileNames;
    private LocalDateTime createTime;
}
