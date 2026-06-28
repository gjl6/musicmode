package com.gjl.music.playback.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface PlayCountMapper {

    /** 递增播放计数。MySQL: ON DUPLICATE KEY UPDATE + 30s 窗口；H2: ON DUPLICATE KEY UPDATE（去重靠 Caffeine） */
    int incPlayCount(@Param("userId") Long userId, @Param("itemId") Long itemId,
                     @Param("itemType") String itemType);

    /** 批量查歌曲播放次数。返回 Map: itemId → play_count */
    @org.apache.ibatis.annotations.MapKey("itemId")
    Map<Long, Integer> batchGetSongCounts(@Param("userId") Long userId,
                                           @Param("songIds") List<Long> songIds);

    /** 单条查询 */
    Integer getPlayCount(@Param("userId") Long userId, @Param("itemId") Long itemId,
                         @Param("itemType") String itemType);

    /** 按 item 清理（歌曲/专辑/艺术家删除时） */
    int deleteByItem(@Param("itemId") Long itemId, @Param("itemType") String itemType);

    /** 获取播放量最高的艺术家 ID 列表（全局或按用户聚合）。
     *  返回 List<Map>，每项含 artistId (Long) 和 totalPlays (Integer)。 */
    List<Map<String, Object>> getTopArtistIds(@Param("userId") Long userId,
                                              @Param("limit") int limit);
}
