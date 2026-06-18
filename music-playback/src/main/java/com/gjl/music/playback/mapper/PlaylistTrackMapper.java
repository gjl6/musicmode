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


    void updatePosition(@Param("playlistId") Long playlistId,
                        @Param("oldPosition") int oldPosition,
                        @Param("newPosition") int newPosition);


    void setPosition(@Param("playlistId") Long playlistId,
                     @Param("id") Long id,
                     @Param("newPosition") int newPosition);


    List<Long> findPlaylistIdsBySongId(@Param("songId") Long songId);


    List<PlaylistTrack> findFirstNByPlaylistId(@Param("playlistId") Long playlistId,
                                               @Param("limit") int limit);


    List<PlaylistTrack> findFirstNByPlaylistIds(@Param("playlistIds") List<Long> playlistIds,
                                                @Param("limit") int limit);
}
