package com.gjl.music.persistence;

import com.gjl.music.infra.util.PinyinUtils;
import com.gjl.music.mapper.MusicMapper;
import com.gjl.music.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;


@Slf4j
@Component
public class MetadataPersisterImpl implements MetadataPersister {

    protected final MusicMapper mapper;

    public MetadataPersisterImpl(MusicMapper mapper) {
        this.mapper = mapper;
    }

    private record FileRow(String filePath, Song song, String albumName,
                           List<String> artistNames, List<String> styleNames,
                           List<Lyric> lyrics) {}

    @Override
    @Transactional
    public void persistBatch(List<Map.Entry<String, MusicMetadata>> batch) {
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

                Map<String, Long> artistNameToId = Map.of();
        if (!artistByName.isEmpty()) {
            for (Artist a : artistByName.values()) {
                a.setSortArtistName(PinyinUtils.toSortKey(a.getArtistName()));
            }
            mapper.batchUpsertArtists(new ArrayList<>(artistByName.values()));
            artistNameToId = lookupIds(
                    mapper.selectArtistIdsByNames(new ArrayList<>(artistByName.keySet())),
                    "artist_name");
        }

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
            mapper.batchUpsertAlbums(new ArrayList<>(albumByName.values()));
            albumNameToId = lookupIds(
                    mapper.selectAlbumIdsByNames(new ArrayList<>(albumByName.keySet())),
                    "album_name");
        }

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
        mapper.batchUpsertSongs(songsToUpsert);
        Map<String, Long> filePathToSongId = Map.of();
        List<String> filePaths = rows.stream()
                .map(FileRow::filePath).toList();
        if (!filePaths.isEmpty()) {
            filePathToSongId = lookupIds(
                    mapper.selectSongIdsByFilePaths(filePaths), "file_path");
        }

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
            mapper.batchInsertSongArtists(saRelations);
        }

                Map<String, Long> styleNameToId = Map.of();
        if (!styleByName.isEmpty()) {
            for (Style st : styleByName.values()) {
                st.setSortStyleName(PinyinUtils.toSortKey(st.getStyleName()));
            }
            mapper.batchUpsertStyles(new ArrayList<>(styleByName.values()));
            styleNameToId = lookupIds(
                    mapper.selectStyleIdsByNames(new ArrayList<>(styleByName.keySet())),
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
                mapper.batchInsertSongStyles(ssRelations);
            }
        }

                if (!allLyrics.isEmpty()) {
            for (int i = 0; i < rows.size(); i++) {
                FileRow row = rows.get(i);
                Long songId = filePathToSongId.get(row.filePath());
                if (songId == null) continue;
                for (Lyric lyric : row.lyrics) {
                    lyric.setSongId(songId);
                }
            }
            mapper.batchUpsertLyrics(allLyrics);
        }

                if (!albumNameToId.isEmpty()) {
            mapper.updateAlbumSongCounts(new ArrayList<>(albumNameToId.values()));
        }
        if (!artistNameToId.isEmpty()) {
            List<Long> artistIds = new ArrayList<>(artistNameToId.values());
            mapper.updateArtistAlbumCounts(artistIds);
            mapper.updateArtistSongCounts(artistIds);
        }
        if (!styleNameToId.isEmpty()) {
            mapper.updateStyleSongCounts(new ArrayList<>(styleNameToId.values()));
        }
    }


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
