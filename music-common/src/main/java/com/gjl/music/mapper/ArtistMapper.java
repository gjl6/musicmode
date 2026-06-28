package com.gjl.music.mapper;

import com.gjl.music.model.Album;
import com.gjl.music.model.Artist;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface ArtistMapper {

    void upsertArtist(Artist artist);

    Long selectArtistIdByName(String artistName);

    void batchUpsertArtists(@Param("list") List<Artist> list);

    /** 批量插入艺术家（已存在则忽略，不覆盖任何字段）。用于 Song 管道场景 */
    int batchInsertArtistsIgnoreExisting(@Param("list") List<Artist> artists);

    List<Map<String, Object>> selectArtistIdsByNames(@Param("names") List<String> names);

    /** 更新艺术家字段。⚠️ artistName 会被覆写，调用方必须确保传入正确值。 */
    int updateArtist(@Param("id") Long id,
                     @Param("artistName") String artistName,
                     @Param("introduction") String introduction,
                     @Param("gender") Integer gender,
                     @Param("country") String country,
                     @Param("artistCover") String artistCover,
                     @Param("enrichSource") String enrichSource);

    /** 根据 ID 查询艺术家 */
    Artist findArtistById(@Param("id") Long id);

    /** 根据 ID 列表批量查询艺术家 */
    List<Artist> findArtistsByIds(@Param("ids") List<Long> ids);

    /** 查询全部艺术家 ID（用于 Lucene 全量索引构建） */
    List<Long> findAllArtistIds();

    /** 统计艺术家总数（用于索引健康检查） */
    int countAllArtists();

    /** 查询指定时间之后更新的艺术家 ID（用于 Lucene 增量索引同步） */
    List<Long> findArtistIdsUpdatedAfter(@Param("since") java.time.LocalDateTime since);

    /** 按艺术家 ID 查询专辑 */
    List<Album> findAlbumsByArtistId(@Param("artistId") Long artistId);
}
