package com.gjl.music.mapper;

import com.gjl.music.model.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface MusicMapper {

    void upsertArtist(Artist artist);
    Long selectArtistIdByName(String artistName);
    void batchUpsertArtists(@Param("list") List<Artist> list);
    List<Map<String, Object>> selectArtistIdsByNames(@Param("names") List<String> names);

    void upsertAlbum(Album album);
    Long selectAlbumIdByName(String albumName);
    void batchUpsertAlbums(@Param("list") List<Album> list);
    List<Map<String, Object>> selectAlbumIdsByNames(@Param("names") List<String> names);

    int updateArtist(@Param("id") Long id,
                     @Param("artistName") String artistName,
                     @Param("introduction") String introduction,
                     @Param("gender") Integer gender,
                     @Param("country") String country,
                     @Param("artistCover") String artistCover,
                     @Param("enrichSource") String enrichSource);

    int updateAlbum(@Param("id") Long id,
                    @Param("albumName") String albumName,
                    @Param("albumType") String albumType,
                    @Param("albumYear") Integer albumYear,
                    @Param("introduction") String introduction,
                    @Param("company") String company,
                    @Param("language") String language);

    void upsertSong(@Param("song") Song song, @Param("albumId") Long albumId);
    Long selectSongIdByFilePath(String filePath);
    void batchUpsertSongs(@Param("list") List<Song> list);
    List<Map<String, Object>> selectSongIdsByFilePaths(@Param("paths") List<String> paths);

    void upsertStyle(Style style);
    Long selectStyleIdByName(String styleName);
    Style findStyleById(@Param("id") Long id);


    Map<String, Object> selectStyleInfoByName(@Param("styleName") String styleName);
    void batchUpsertStyles(@Param("list") List<Style> list);

    int updateStyle(@Param("id") Long id,
                    @Param("styleName") String styleName,
                    @Param("description") String description,
                    @Param("styleImage") String styleImage);
    List<Map<String, Object>> selectStyleIdsByNames(@Param("names") List<String> names);

    void upsertLyric(Lyric lyric);
    void batchUpsertLyrics(@Param("list") List<Lyric> list);


    Map<String, Object> findLyricBySongId(@Param("songId") Long songId);

    void insertSongArtist(@Param("songId") Long songId, @Param("artistId") Long artistId, @Param("sortOrder") int sortOrder);
    void insertSongStyle(@Param("songId") Long songId, @Param("styleId") Long styleId);
    void batchInsertSongArtists(@Param("list") List<Map<String, Object>> list);
    void batchInsertSongStyles(@Param("list") List<Map<String, Object>> list);


    List<Map<String, Object>> findHashDuplicates(@Param("rootPath") String rootPath);


    List<Map<String, Object>> findFileNameDuplicates(@Param("rootPaths") List<String> rootPaths);


    List<Map<String, Object>> findMetadataForDedup(@Param("rootPaths") List<String> rootPaths);


    List<Map<String, Object>> findFingerprintsForDedup(@Param("rootPath") String rootPath);


    List<Map<String, Object>> findAllSongsUnderRoot(@Param("rootPaths") List<String> rootPaths);


    void batchUpdateSongHash(@Param("list") List<Map<String, Object>> list);


    void batchUpdateSongFingerprint(@Param("list") List<Map<String, Object>> list);


    List<Map<String, Object>> findFingerprintsByPaths(@Param("paths") List<String> paths);


    List<Map<String, Object>> findMetadataByPaths(@Param("paths") List<String> paths);


    List<Map<String, Object>> findFullMetadataByPaths(@Param("paths") List<String> paths);


    List<Map<String, Object>> selectSongPathsUnderRoot(@Param("rootPath") String rootPath);


    List<Map<String, Object>> selectSongPathsUnderDirs(@Param("dirs") List<String> dirs);


    void deleteSongArtistsBySongIds(@Param("songIds") List<Long> songIds);


    void deleteSongStylesBySongIds(@Param("songIds") List<Long> songIds);


    void deleteLyricsBySongIds(@Param("songIds") List<Long> songIds);


    void deleteSongsByIds(@Param("songIds") List<Long> songIds);


    void updateSongFilePath(@Param("oldPath") String oldPath,
                            @Param("newPath") String newPath,
                            @Param("newFileName") String newFileName);


    Song findSongById(@Param("id") Long id);


    Song findSongByFilePath(@Param("filePath") String filePath);


    List<Song> findSongsByIds(@Param("ids") List<Long> ids);


    Album findAlbumById(@Param("id") Long id);


    List<Album> findAlbumsByIds(@Param("ids") List<Long> ids);


    Artist findArtistById(@Param("id") Long id);


    Integer findFirstArtistIdBySongId(@Param("songId") Long songId);


    String findFirstGenreBySongId(@Param("songId") Long songId);


    List<Map<String, Object>> findGenresBySongIds(@Param("songIds") List<Long> songIds);


    List<Artist> findArtistsByIds(@Param("ids") List<Long> ids);


    List<Style> findStylesByIds(@Param("ids") List<Long> ids);


    List<Long> findAllSongIds();


    List<Long> findSongIdsUpdatedAfter(@Param("since") java.time.LocalDateTime since);


    List<Artist> findArtists(@Param("letter") String letter, @Param("sort") String sort,
                             @Param("offset") int offset, @Param("limit") int limit);


    int countArtists(@Param("letter") String letter);


    List<Map<String, Object>> getArtistLetters();


    List<Album> findAlbumsByType(@Param("type") String type, @Param("offset") int offset,
                                  @Param("limit") int limit, @Param("userId") Long userId,
                                  @Param("letter") String letter, @Param("starred") Boolean starred,
                                  @Param("starredUsername") String starredUsername,
                                  @Param("artistId") Long artistId);


    int countAlbumsByLetter(@Param("letter") String letter, @Param("starred") Boolean starred,
                            @Param("starredUsername") String starredUsername,
                            @Param("artistId") Long artistId);


    List<Map<String, Object>> getAlbumLetters(@Param("starred") Boolean starred,
                                              @Param("starredUsername") String starredUsername);


    List<Song> findSongsByAlbumIds(@Param("albumIds") List<Long> albumIds);


    List<Album> findAlbumsByArtistId(@Param("artistId") Long artistId);


    List<Song> findSongsByAlbumId(@Param("albumId") Long albumId);


    List<Song> findSongsByArtistId(@Param("artistId") Long artistId);


    List<Song> findSongsPaginated(@Param("offset") int offset, @Param("limit") int limit,
                                  @Param("letter") String letter, @Param("sort") String sort,
                                  @Param("userId") Long userId, @Param("artistId") Long artistId);


    int countSongs(@Param("letter") String letter, @Param("artistId") Long artistId);


    List<Map<String, Object>> getSongLetters();


    List<Song> findRandomSongs(@Param("limit") int limit);


    List<Map<String, Object>> findDistinctGenres();


    List<Map<String, Object>> findGenresPaginated(@Param("letter") String letter,
                                                   @Param("sort") String sort,
                                                   @Param("offset") int offset,
                                                   @Param("limit") int limit);


    int countGenres(@Param("letter") String letter);


    List<Map<String, Object>> getGenreLetters();


    List<Song> findSongsByGenre(@Param("genre") String genre,
                                @Param("letter") String letter,
                                @Param("sort") String sort,
                                @Param("offset") int offset,
                                @Param("limit") int limit,
                                @Param("userId") Long userId);


    int countSongsByGenre(@Param("genre") String genre, @Param("letter") String letter);


    List<Map<String, Object>> findArtistIndex();


    List<Long> findArtistIdsBySongId(@Param("songId") Long songId);


    List<Long> findStyleIdsBySongId(@Param("songId") Long songId);


    List<Long> findSongIdsByStyleId(@Param("styleId") Long styleId);


    Long findUserIdByUsername(@Param("username") String username);


    String findFirstSongCoverByAlbumId(@Param("albumId") Long albumId);


    String findFirstAlbumCoverByArtistId(@Param("artistId") Long artistId);


    String findFirstSongCoverByArtistId(@Param("artistId") Long artistId);


    List<Long> findAlbumIdsBySongIds(@Param("songIds") List<Long> songIds);


    List<Long> findArtistIdsBySongIds(@Param("songIds") List<Long> songIds);


    void updateAlbumSongCounts(@Param("albumIds") List<Long> albumIds);


    void updateArtistAlbumCounts(@Param("artistIds") List<Long> artistIds);


    void updateArtistSongCounts(@Param("artistIds") List<Long> artistIds);


    List<Long> findStyleIdsBySongIds(@Param("songIds") List<Long> songIds);


    void updateStyleSongCounts(@Param("styleIds") List<Long> styleIds);


    List<Artist> searchArtists(@Param("keyword") String keyword,
                               @Param("letter") String letter,
                               @Param("sort") String sort,
                               @Param("offset") int offset,
                               @Param("limit") int limit);


    int countSearchArtists(@Param("keyword") String keyword,
                           @Param("letter") String letter);


    List<Map<String, Object>> findDuplicateArtistNames();


    List<Artist> findArtistsByNormalizedName(@Param("normName") String normName);


    int reassignSongArtists(@Param("sourceId") Long sourceId,
                            @Param("targetId") Long targetId);


    int deleteConflictingSongArtists(@Param("sourceId") Long sourceId,
                                      @Param("targetId") Long targetId);


    int reassignAlbumArtist(@Param("sourceId") Long sourceId,
                            @Param("targetId") Long targetId);


    int deleteArtist(@Param("id") Long id);


    List<Long> findAllArtistIds(@Param("letter") String letter,
                                @Param("keyword") String keyword,
                                @Param("minSongs") Integer minSongs,
                                @Param("maxSongs") Integer maxSongs,
                                @Param("style") String style,
                                @Param("country") String country);


    List<Long> findArtistIdsByLetter(@Param("letter") String letter,
                                     @Param("keyword") String keyword,
                                     @Param("minSongs") Integer minSongs,
                                     @Param("maxSongs") Integer maxSongs,
                                     @Param("style") String style,
                                     @Param("country") String country);


    List<Long> findArtistIdsByKeyword(@Param("keyword") String keyword,
                                      @Param("letter") String letter,
                                      @Param("minSongs") Integer minSongs,
                                      @Param("maxSongs") Integer maxSongs,
                                      @Param("style") String style,
                                      @Param("country") String country);


    List<Long> findIncompleteArtistIds(@Param("letter") String letter,
                                       @Param("keyword") String keyword,
                                       @Param("minSongs") Integer minSongs,
                                       @Param("maxSongs") Integer maxSongs,
                                       @Param("style") String style,
                                       @Param("country") String country);


    List<Long> findUnenrichedArtistIds(@Param("letter") String letter,
                                       @Param("keyword") String keyword,
                                       @Param("minSongs") Integer minSongs,
                                       @Param("maxSongs") Integer maxSongs,
                                       @Param("style") String style,
                                       @Param("country") String country);


    List<Long> findNonstandardArtistIds(@Param("letter") String letter,
                                        @Param("keyword") String keyword,
                                        @Param("minSongs") Integer minSongs,
                                        @Param("maxSongs") Integer maxSongs,
                                        @Param("style") String style,
                                        @Param("country") String country);


    List<Long> findNakedArtistIds(@Param("letter") String letter,
                                  @Param("keyword") String keyword,
                                  @Param("minSongs") Integer minSongs,
                                  @Param("maxSongs") Integer maxSongs,
                                  @Param("style") String style,
                                  @Param("country") String country);


    List<Long> findDuplicateArtistIds(@Param("letter") String letter,
                                      @Param("keyword") String keyword,
                                      @Param("minSongs") Integer minSongs,
                                      @Param("maxSongs") Integer maxSongs,
                                      @Param("style") String style,
                                      @Param("country") String country);

}
