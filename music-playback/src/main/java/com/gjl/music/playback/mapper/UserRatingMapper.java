package com.gjl.music.playback.mapper;

import com.gjl.music.playback.model.UserRating;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface UserRatingMapper {

    void upsert(UserRating rating);

    void delete(@Param("userId") Long userId, @Param("itemId") Long itemId,
                @Param("itemType") String itemType);

    UserRating findByUserItem(@Param("userId") Long userId, @Param("itemId") Long itemId,
                              @Param("itemType") String itemType);


    @org.apache.ibatis.annotations.MapKey("itemId")
    Map<Long, Map<String, Object>> getAverageRatings(@Param("itemType") String itemType,
                                                       @Param("itemIds") List<Long> itemIds);


    int deleteByItem(@Param("itemId") Long itemId, @Param("itemType") String itemType);


    @org.apache.ibatis.annotations.MapKey("itemId")
    Map<Long, Integer> batchGetUserRatings(@Param("userId") Long userId,
                                           @Param("itemType") String itemType,
                                           @Param("itemIds") List<Long> itemIds);
}
