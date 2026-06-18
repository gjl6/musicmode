package com.gjl.music.mapper;

import com.gjl.music.model.FileWatchSnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FileWatchSnapshotMapper {

    List<FileWatchSnapshot> selectAll();

    void batchUpsert(@Param("list") List<FileWatchSnapshot> snapshots);
}
