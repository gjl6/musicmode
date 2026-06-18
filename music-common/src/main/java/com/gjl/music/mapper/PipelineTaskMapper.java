package com.gjl.music.mapper;

import com.gjl.music.model.PipelineTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PipelineTaskMapper {

    void insert(PipelineTask task);

    void update(PipelineTask task);

    void updateState(@Param("id") String id, @Param("state") String state);

    void updateStateWithDuration(@Param("id") String id, @Param("state") String state, @Param("durationMs") long durationMs);

    void updateProgress(@Param("id") String id,
                        @Param("totalFiles") int totalFiles,
                        @Param("successFiles") int successFiles,
                        @Param("failedFiles") int failedFiles,
                        @Param("errorNode") String errorNode,
                        @Param("errorMessage") String errorMessage);

    PipelineTask selectById(String id);

    List<PipelineTask> selectByNonTerminal();

    List<PipelineTask> selectAll();

    void deleteById(String id);

    void deleteExpired(@Param("before") java.time.LocalDateTime before);
}
