package com.gjl.music.mapper;

import com.gjl.music.model.Lyric;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface LyricMapper {

    void upsertLyric(Lyric lyric);

    void batchUpsertLyrics(@Param("list") List<Lyric> list);

    /** 根据歌曲 ID 查询歌词 */
    Map<String, Object> findLyricBySongId(@Param("songId") Long songId);

    /** 批量查询歌词（返回 songId → lyricContent 映射） */
    List<Map<String, Object>> findLyricsBySongIds(@Param("songIds") List<Long> songIds);

    /** 搜索歌词内容，返回匹配的歌曲 ID 列表 */
    List<Long> searchLyricSongIds(@Param("query") String query, @Param("limit") int limit);
}
