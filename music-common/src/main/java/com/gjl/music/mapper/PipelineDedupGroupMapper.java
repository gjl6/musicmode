package com.gjl.music.mapper;

import com.gjl.music.model.PipelineDedupGroup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PipelineDedupGroupMapper {

    void insert(PipelineDedupGroup group);

    void batchInsert(@Param("groups") List<PipelineDedupGroup> groups);

    List<PipelineDedupGroup> selectByPipelineId(@Param("pipelineId") String pipelineId);

    List<PipelineDedupGroup> selectByPipelineIdAndStrategy(
            @Param("pipelineId") String pipelineId,
            @Param("strategy") String strategy);

    void deleteByPipelineId(@Param("pipelineId") String pipelineId);

    void update(PipelineDedupGroup group);

    void deleteEmptyGroups(@Param("pipelineId") String pipelineId);

    void deleteById(@Param("id") Long id);

    void deleteExpired(@Param("before") java.time.LocalDateTime before);
}
