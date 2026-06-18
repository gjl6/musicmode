package com.gjl.music.playback.mapper;

import com.gjl.music.playback.model.PlayHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PlayHistoryMapper {

    void insert(PlayHistory ph);

    List<PlayHistory> findByUserAndSong(@Param("userId") Long userId,
                                         @Param("songId") Long songId,
                                         @Param("offset") int offset,
                                         @Param("limit") int limit);

    int deleteBySongId(@Param("songId") Long songId);
}
