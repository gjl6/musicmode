package com.gjl.music.playback.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface PlayCountMapper {


    int incPlayCount(@Param("userId") Long userId, @Param("itemId") Long itemId,
                     @Param("itemType") String itemType);


    @org.apache.ibatis.annotations.MapKey("itemId")
    Map<Long, Integer> batchGetSongCounts(@Param("userId") Long userId,
                                           @Param("songIds") List<Long> songIds);


    Integer getPlayCount(@Param("userId") Long userId, @Param("itemId") Long itemId,
                         @Param("itemType") String itemType);


    int deleteByItem(@Param("itemId") Long itemId, @Param("itemType") String itemType);
}
