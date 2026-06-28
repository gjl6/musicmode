package com.gjl.music.editor.mapper;

import com.gjl.music.model.PipelineItemLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Mapper
public interface PipelineItemLogMapper {

    void batchInsert(@Param("list") List<PipelineItemLog> logs);

    Set<String> selectSuccessKeys(@Param("pipelineId") String pipelineId);

    List<PipelineItemLog> selectByPipeline(@Param("pipelineId") String pipelineId);

    List<PipelineItemLog> selectPage(@Param("pipelineId") String pipelineId,
                                     @Param("status") String status,
                                     @Param("offset") int offset,
                                     @Param("limit") int limit);

    int countByPipelineStatus(@Param("pipelineId") String pipelineId,
                              @Param("status") String status);

    void deleteByPipeline(String pipelineId);

    void deleteExpired(@Param("before") LocalDateTime before);
}
