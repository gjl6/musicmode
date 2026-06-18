package com.gjl.music.playback.mapper;

import com.gjl.music.playback.model.UserStarred;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserStarredMapper {

    void insert(UserStarred star);

    int delete(@Param("userId") Long userId, @Param("itemId") Long itemId,
               @Param("itemType") String itemType);

    List<Long> findStarredIds(@Param("userId") Long userId, @Param("itemType") String itemType);

    boolean isStarred(@Param("userId") Long userId, @Param("itemId") Long itemId,
                      @Param("itemType") String itemType);


    int deleteByItem(@Param("itemId") Long itemId, @Param("itemType") String itemType);
}
