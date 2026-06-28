package com.gjl.music.playback.mapper;

import com.gjl.music.playback.model.PlaylistTrack;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PlaylistTrackMapper {

    List<PlaylistTrack> findByPlaylistId(@Param("playlistId") Long playlistId);

    void insert(PlaylistTrack track);

    void batchInsert(@Param("list") List<PlaylistTrack> tracks);

    void deleteByPlaylistId(@Param("playlistId") Long playlistId);

    void deleteByPlaylistAndPosition(@Param("playlistId") Long playlistId,
                                     @Param("position") int position);

    int maxPosition(@Param("playlistId") Long playlistId);

    /** 更新单条曲目的 position（用于大间隔插入） */
    void updatePosition(@Param("playlistId") Long playlistId,
                        @Param("oldPosition") int oldPosition,
                        @Param("newPosition") int newPosition);

    /** 设置单条曲目的 position（程序化 renumber 使用） */
    void setPosition(@Param("playlistId") Long playlistId,
                     @Param("id") Long id,
                     @Param("newPosition") int newPosition);

    /** 查询歌曲所属的歌单 ID 列表 */
    List<Long> findPlaylistIdsBySongId(@Param("songId") Long songId);

    /** 查询歌单前 N 首曲目（按 position 排序） */
    List<PlaylistTrack> findFirstNByPlaylistId(@Param("playlistId") Long playlistId,
                                               @Param("limit") int limit);

    /** 批量查询多个歌单的前 N 首曲目（一次查询替代 N+1） */
    List<PlaylistTrack> findFirstNByPlaylistIds(@Param("playlistIds") List<Long> playlistIds,
                                                @Param("limit") int limit);
}
