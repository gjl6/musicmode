package com.gjl.music.playback.mapper;

import com.gjl.music.playback.model.Playlist;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PlaylistMapper {

    List<Playlist> findByOwner(@Param("owner") String owner);

    Playlist findById(@Param("id") Long id);

    /** 批量查询歌单 */
    List<Playlist> findByIds(@Param("ids") List<Long> ids);

    void insert(Playlist playlist);

    void update(Playlist playlist);

    void deleteById(@Param("id") Long id);

    void updateSongCount(@Param("id") Long id);
}
