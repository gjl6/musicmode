package com.gjl.music.mapper;

import com.gjl.music.model.Album;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface AlbumMapper {

    void upsertAlbum(Album album);

    Long selectAlbumIdByName(String albumName);

    void batchUpsertAlbums(@Param("list") List<Album> list);

    /** 批量插入专辑（已存在则忽略，不覆盖任何字段）。用于 Song 管道场景 */
    int batchInsertAlbumsIgnoreExisting(@Param("list") List<Album> albums);

    List<Map<String, Object>> selectAlbumIdsByNames(@Param("names") List<String> names);

    int updateAlbum(@Param("id") Long id,
                    @Param("albumName") String albumName,
                    @Param("albumType") String albumType,
                    @Param("albumYear") Integer albumYear,
                    @Param("introduction") String introduction,
                    @Param("company") String company,
                    @Param("language") String language,
                    @Param("albumCover") String albumCover,
                    @Param("enrichSource") String enrichSource);

    /** 根据 ID 查询专辑 */
    Album findAlbumById(@Param("id") Long id);

    /** 根据 ID 列表批量查询专辑 */
    List<Album> findAlbumsByIds(@Param("ids") List<Long> ids);

    /** 查询全部专辑 ID（用于 Lucene 全量索引构建） */
    List<Long> findAllAlbumIds();

    /** 统计专辑总数（用于索引健康检查） */
    int countAllAlbums();

    /** 查询指定时间之后更新的专辑 ID（用于 Lucene 增量索引同步） */
    List<Long> findAlbumIdsUpdatedAfter(@Param("since") java.time.LocalDateTime since);
}
