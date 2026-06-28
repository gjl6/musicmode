package com.gjl.music.playback.mapper;

import com.gjl.music.playback.model.PlayQueueEntry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PlayQueueMapper {

    List<PlayQueueEntry> findByUsername(@Param("username") String username);

    void insert(PlayQueueEntry entry);

    void clearByUsername(@Param("username") String username);

    void deleteByUsernameAndPosition(@Param("username") String username,
                                     @Param("position") int position);

    void batchInsert(@Param("list") List<PlayQueueEntry> entries);

    /** 获取最近活跃的队列条目（跨用户），用于 Subsonic getNowPlaying */
    List<PlayQueueEntry> findRecentActive(@Param("limit") int limit);
}
