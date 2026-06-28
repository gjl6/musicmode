package com.gjl.music.persistence;

import com.gjl.music.infra.util.PinyinUtils;
import com.gjl.music.mapper.SongMapper;
import com.gjl.music.mapper.ArtistMapper;
import com.gjl.music.mapper.AlbumMapper;
import com.gjl.music.mapper.StyleMapper;
import com.gjl.music.mapper.LyricMapper;
import com.gjl.music.model.*;
import com.gjl.music.search.EntityChangeEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 元数据批量持久化实现 —— 纯批量写入，不含 Pipeline 概念。
 *
 * <p>编排流程：</p>
 * <ol>
 *   <li>收集 + 去重 Artist / Album / Style / Lyric</li>
 *   <li>批量 upsert artists（生成拼音排序键）→ 回查 ID</li>
 *   <li>关联 album.artistId → 批量 upsert albums → 回查 ID</li>
 *   <li>关联 song.albumId → 批量 upsert songs → 回查 ID</li>
 *   <li>批量插入 song_artist（保留排序）</li>
 *   <li>批量 upsert styles + 插入 song_style</li>
 *   <li>关联 lyric.songId → 批量 upsert lyrics</li>
 *   <li>刷新 album/artist/style 冗余计数</li>
 * </ol>
 */
@Slf4j
@Component
public class MetadataPersisterImpl implements MetadataPersister {

    protected final SongMapper songMapper;
    protected final ArtistMapper artistMapper;
    protected final AlbumMapper albumMapper;
    protected final StyleMapper styleMapper;
    protected final LyricMapper lyricMapper;
    protected final ApplicationEventPublisher eventPublisher;

    public MetadataPersisterImpl(SongMapper songMapper,
                                  ArtistMapper artistMapper,
                                  AlbumMapper albumMapper,
                                  StyleMapper styleMapper,
                                  LyricMapper lyricMapper,
                                  ApplicationEventPublisher eventPublisher) {
        this.songMapper = songMapper;
        this.artistMapper = artistMapper;
        this.albumMapper = albumMapper;
        this.styleMapper = styleMapper;
        this.lyricMapper = lyricMapper;
        this.eventPublisher = eventPublisher;
    }

    private record FileRow(String filePath, Song song, String albumName,
                           List<String> artistNames, List<String> styleNames,
                           List<Lyric> lyrics) {}

    @Override
    @Transactional
    public void persistBatch(List<Map.Entry<String, MusicMetadata>> batch) {
        doPersistBatch(batch, false);
    }

    @Override
    @Transactional
    public void persistBatchMergeArtistAlbum(List<Map.Entry<String, MusicMetadata>> batch) {
        doPersistBatch(batch, true);
    }

    private void doPersistBatch(List<Map.Entry<String, MusicMetadata>> batch, boolean mergeArtistAlbum) {
        // ── 1. 收集 + 去重 ──
        Map<String, Artist> artistByName = new LinkedHashMap<>();
        Map<String, Album> albumByName = new LinkedHashMap<>();
        Map<String, Style> styleByName = new LinkedHashMap<>();
        List<Lyric> allLyrics = new ArrayList<>();
        List<FileRow> rows = new ArrayList<>();

        for (var entry : batch) {
            MusicMetadata meta = entry.getValue();
            if (meta.getImmutableSongs().isEmpty()) continue;

            List<String> artistNames = new ArrayList<>();
            for (Artist a : meta.getImmutableArtists()) {
                if (!isBlank(a.getArtistName())) {
                    artistByName.putIfAbsent(a.getArtistName(), a);
                    artistNames.add(a.getArtistName());
                }
            }

            String albumName = null;
            for (Album a : meta.getImmutableAlbums()) {
                if (!isBlank(a.getAlbumName())) {
                    albumByName.putIfAbsent(a.getAlbumName(), a);
                    albumName = a.getAlbumName();
                }
            }

            List<String> styleNames = new ArrayList<>();
            for (Style s : meta.getImmutableStyles()) {
                if (!isBlank(s.getStyleName())) {
                    styleByName.putIfAbsent(s.getStyleName(), s);
                    styleNames.add(s.getStyleName());
                }
            }

            List<Lyric> lyrics = new ArrayList<>(meta.getImmutableLyrics());
            allLyrics.addAll(lyrics);
            Song song = meta.getImmutableSongs().getFirst();

            rows.add(new FileRow(entry.getKey(), song, albumName, artistNames, styleNames, lyrics));
        }

        if (rows.isEmpty()) return;

        // ── 2. 批量 upsert artists（生成拼音排序键）+ 回查 ID ──
        Map<String, Long> artistNameToId = Map.of();
        if (!artistByName.isEmpty()) {
            for (Artist a : artistByName.values()) {
                a.setSortArtistName(PinyinUtils.toSortKey(a.getArtistName()));
            }
            if (mergeArtistAlbum) {
                artistMapper.batchUpsertArtists(new ArrayList<>(artistByName.values()));
            } else {
                artistMapper.batchInsertArtistsIgnoreExisting(new ArrayList<>(artistByName.values()));
            }
            artistNameToId = lookupIds(
                    artistMapper.selectArtistIdsByNames(new ArrayList<>(artistByName.keySet())),
                    "artist_name");
        }

        // ── 3. 关联 album.artistId + 批量 upsert albums（生成拼音排序键）+ 回查 ID ──
        Map<String, Long> albumNameToId = Map.of();
        if (!albumByName.isEmpty()) {
            for (Album al : albumByName.values()) {
                al.setSortAlbumName(PinyinUtils.toSortKey(al.getAlbumName()));
            }
            for (FileRow row : rows) {
                if (row.albumName != null && !row.artistNames.isEmpty()) {
                    Long artistId = artistNameToId.get(row.artistNames.getFirst());
                    if (artistId != null) {
                        Album album = albumByName.get(row.albumName);
                        if (album != null && album.getArtistId() == null) {
                            album.setArtistId(artistId.intValue());
                        }
                    }
                }
            }
            if (mergeArtistAlbum) {
                albumMapper.batchUpsertAlbums(new ArrayList<>(albumByName.values()));
            } else {
                albumMapper.batchInsertAlbumsIgnoreExisting(new ArrayList<>(albumByName.values()));
            }
            albumNameToId = lookupIds(
                    albumMapper.selectAlbumIdsByNames(new ArrayList<>(albumByName.keySet())),
                    "album_name");
        }

        // ── 4. 关联 song.albumId + 批量 upsert songs + 回查 ID ──
        List<Song> songsToUpsert = new ArrayList<>();
        for (FileRow row : rows) {
            Song s = row.song;
            s.setSortTitle(PinyinUtils.toSortKey(s.getTitle()));
            if (row.albumName != null) {
                Long albumId = albumNameToId.get(row.albumName);
                if (albumId != null) {
                    s = s.toBuilder().albumId(albumId.intValue()).build();
                }
            }
            songsToUpsert.add(s);
        }
        songMapper.batchUpsertSongs(songsToUpsert);
        Map<String, Long> filePathToSongId = Map.of();
        List<String> filePaths = rows.stream()
                .map(FileRow::filePath).toList();
        if (!filePaths.isEmpty()) {
            filePathToSongId = lookupIds(
                    songMapper.selectSongIdsByFilePaths(filePaths), "file_path");
        }

        // ── 5. 批量插入 song_artist（记录排序以保持艺术家顺序）──
        List<Map<String, Object>> saRelations = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            FileRow row = rows.get(i);
            Long songId = filePathToSongId.get(row.filePath());
            if (songId == null) continue;
            for (int idx = 0; idx < row.artistNames.size(); idx++) {
                String artistName = row.artistNames.get(idx);
                Long artistId = artistNameToId.get(artistName);
                if (artistId != null) {
                    saRelations.add(Map.of("songId", songId, "artistId", artistId, "sortOrder", idx));
                }
            }
        }
        if (!saRelations.isEmpty()) {
            songMapper.batchInsertSongArtists(saRelations);
        }

        // ── 6. 批量 upsert styles（生成拼音排序键）+ 批量插入 song_style ──
        Map<String, Long> styleNameToId = Map.of();
        if (!styleByName.isEmpty()) {
            for (Style st : styleByName.values()) {
                st.setSortStyleName(PinyinUtils.toSortKey(st.getStyleName()));
            }
            styleMapper.batchUpsertStyles(new ArrayList<>(styleByName.values()));
            styleNameToId = lookupIds(
                    styleMapper.selectStyleIdsByNames(new ArrayList<>(styleByName.keySet())),
                    "style_name");

            List<Map<String, Object>> ssRelations = new ArrayList<>();
            for (int i = 0; i < rows.size(); i++) {
                FileRow row = rows.get(i);
                Long songId = filePathToSongId.get(row.filePath());
                if (songId == null) continue;
                for (String styleName : row.styleNames) {
                    Long styleId = styleNameToId.get(styleName);
                    if (styleId != null) {
                        ssRelations.add(Map.of("songId", songId, "styleId", styleId));
                    }
                }
            }
            if (!ssRelations.isEmpty()) {
                songMapper.batchInsertSongStyles(ssRelations);
            }
        }

        // ── 7. 关联 lyric.songId + 批量 upsert lyrics ──
        if (!allLyrics.isEmpty()) {
            for (int i = 0; i < rows.size(); i++) {
                FileRow row = rows.get(i);
                Long songId = filePathToSongId.get(row.filePath());
                if (songId == null) continue;
                for (Lyric lyric : row.lyrics) {
                    lyric.setSongId(songId);
                }
            }
            lyricMapper.batchUpsertLyrics(allLyrics);
        }

        // ── 8. 刷新受影响的专辑/艺术家/风格冗余计数 ──
        if (!albumNameToId.isEmpty()) {
            songMapper.updateAlbumSongCounts(new ArrayList<>(albumNameToId.values()));
        }
        if (!artistNameToId.isEmpty()) {
            List<Long> artistIds = new ArrayList<>(artistNameToId.values());
            songMapper.updateArtistAlbumCounts(artistIds);
            songMapper.updateArtistSongCounts(artistIds);
        }
        if (!styleNameToId.isEmpty()) {
            songMapper.updateStyleSongCounts(new ArrayList<>(styleNameToId.values()));
        }

        // ── 9. 发布索引更新事件（Lucene 实时同步）──
        publishIndexEvents(filePathToSongId, albumNameToId, artistNameToId);
    }

    /** 发布实体变更事件，驱动 Lucene 索引实时更新 */
    private void publishIndexEvents(Map<String, Long> filePathToSongId,
                                     Map<String, Long> albumNameToId,
                                     Map<String, Long> artistNameToId) {
        // 歌曲：upsert 可能新建也可能更新，统一发 UPDATED（reindexSong 会处理两种情况）
        for (Long songId : filePathToSongId.values()) {
            eventPublisher.publishEvent(EntityChangeEvent.songUpdated(songId));
        }
        // 专辑
        for (Long albumId : albumNameToId.values()) {
            eventPublisher.publishEvent(EntityChangeEvent.albumUpdated(albumId));
        }
        // 艺术家
        for (Long artistId : artistNameToId.values()) {
            eventPublisher.publishEvent(EntityChangeEvent.artistUpdated(artistId));
        }
    }

    /** 将 [{name_col: "xxx", id: 123}, ...] 转为 {name → id}，兼容 H2/MySQL 列名大小写 */
    private static Map<String, Long> lookupIds(List<Map<String, Object>> rows, String nameCol) {
        Map<String, Long> result = new LinkedHashMap<>();
        String upperCol = nameCol.toUpperCase();
        for (Map<String, Object> row : rows) {
            Object nameVal = row.get(nameCol);
            if (nameVal == null) nameVal = row.get(upperCol);
            Object idVal = row.get("ID");
            if (idVal == null) idVal = row.get("id");
            if (nameVal != null && idVal instanceof Number n) {
                result.put(nameVal.toString(), n.longValue());
            }
        }
        return result;
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
}
