package com.gjl.music.service;

import com.gjl.music.mapper.MusicMapper;
import com.gjl.music.model.*;
import com.gjl.music.module.song.writer.WriterFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;


@Slf4j
@Service
public class TagSyncService {

    private final MusicMapper musicMapper;
    private final WriterFactory writerFactory;
    private final Executor asyncExecutor;

    public TagSyncService(MusicMapper musicMapper,
                          WriterFactory writerFactory,
                          @Qualifier("foregroundExecutor") Executor asyncExecutor) {
        this.musicMapper = musicMapper;
        this.writerFactory = writerFactory;
        this.asyncExecutor = asyncExecutor;
    }


    public void syncAlbumChange(Long albumId) {
        CompletableFuture.runAsync(() -> doSyncAlbumChange(albumId), asyncExecutor)
                .exceptionally(ex -> {
                    log.error("syncAlbumChange({}) failed", albumId, ex);
                    return null;
                });
    }


    public void syncArtistChange(Long artistId) {
        CompletableFuture.runAsync(() -> doSyncArtistChange(artistId), asyncExecutor)
                .exceptionally(ex -> {
                    log.error("syncArtistChange({}) failed", artistId, ex);
                    return null;
                });
    }


    public void syncStyleChange(Long styleId) {
        CompletableFuture.runAsync(() -> doSyncStyleChange(styleId), asyncExecutor)
                .exceptionally(ex -> {
                    log.error("syncStyleChange({}) failed", styleId, ex);
                    return null;
                });
    }


    private void doSyncAlbumChange(Long albumId) {
        List<Song> songs = musicMapper.findSongsByAlbumId(albumId);
        if (songs == null || songs.isEmpty()) {
            log.info("syncAlbumChange({}): no songs found, skipping", albumId);
            return;
        }
        log.info("syncAlbumChange({}): syncing {} songs", albumId, songs.size());
        for (Song song : songs) {
            syncSongFile(parseLong(song.getId()));
        }
    }

    private void doSyncArtistChange(Long artistId) {
        List<Song> songs = musicMapper.findSongsByArtistId(artistId);
        if (songs == null || songs.isEmpty()) {
            log.info("syncArtistChange({}): no songs found, skipping", artistId);
            return;
        }
        log.info("syncArtistChange({}): syncing {} songs", artistId, songs.size());
        for (Song song : songs) {
            syncSongFile(parseLong(song.getId()));
        }
    }

    private void doSyncStyleChange(Long styleId) {
        List<Long> songIds = musicMapper.findSongIdsByStyleId(styleId);
        if (songIds == null || songIds.isEmpty()) {
            log.info("syncStyleChange({}): no songs found, skipping", styleId);
            return;
        }
        log.info("syncStyleChange({}): syncing {} songs", styleId, songIds.size());
        for (Long songId : songIds) {
            syncSongFile(songId);
        }
    }


    private void syncSongFile(Long songId) {
        try {
            MusicMetadata metadata = buildFullMetadata(songId);
            Song song = metadata.getSongs().get(0);
            File file = new File(song.getFilePath());
            if (!file.exists() || !file.isFile()) {
                log.warn("syncSongFile: file not found: {}", song.getFilePath());
                return;
            }
            writeWithRetry(file, metadata);
            log.debug("syncSongFile({}): written successfully", songId);
        } catch (Exception e) {
            log.error("syncSongFile({}): failed after retry", songId, e);
        }
    }


    MusicMetadata buildFullMetadata(Long songId) {
        Song song = musicMapper.findSongById(songId);
        if (song == null) {
            throw new IllegalArgumentException("Song not found: " + songId);
        }

        MusicMetadata meta = new MusicMetadata();
        meta.addSong(song);

                if (song.getAlbumId() != null) {
            Album album = musicMapper.findAlbumById(song.getAlbumId().longValue());
            if (album != null) {
                meta.addAlbum(album);
            }
        }

                List<Long> artistIds = musicMapper.findArtistIdsBySongId(songId);
        if (artistIds != null && !artistIds.isEmpty()) {
            List<Artist> artists = musicMapper.findArtistsByIds(artistIds);
            if (artists != null) {
                artists.forEach(meta::addArtist);
            }
        }

                List<Long> styleIds = musicMapper.findStyleIdsBySongId(songId);
        if (styleIds != null && !styleIds.isEmpty()) {
            List<Style> styles = musicMapper.findStylesByIds(styleIds);
            if (styles != null) {
                styles.forEach(meta::addStyle);
            }
        }

                Map<String, Object> lyricRow = musicMapper.findLyricBySongId(songId);
        if (lyricRow != null && !lyricRow.isEmpty()) {
            Lyric lyric = new Lyric();
            lyric.setContent((String) lyricRow.get("content"));
            lyric.setLrcPath((String) lyricRow.get("lrcPath"));
            lyric.setType(parseLyricType(lyricRow.get("type")));
            lyric.setLyricHash((String) lyricRow.get("lyricHash"));
            lyric.setSongId(songId);
            meta.addLyric(lyric);
        }

        return meta;
    }

    private void writeWithRetry(File file, MusicMetadata metadata) throws Exception {
        try {
            writerFactory.write(file, metadata);
        } catch (Exception firstAttempt) {
            log.warn("First write attempt failed for {}: {}, retrying...",
                    file.getAbsolutePath(), firstAttempt.getMessage());
            try {
                writerFactory.write(file, metadata);
            } catch (Exception secondAttempt) {
                log.error("Second write attempt also failed for {}: {}",
                        file.getAbsolutePath(), secondAttempt.getMessage());
                throw secondAttempt;
            }
        }
    }

    private LyricType parseLyricType(Object typeVal) {
        if (typeVal == null) return null;
        String s = typeVal.toString();
        try {
            return LyricType.valueOf(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Long parseLong(String s) {
        if (s == null) return null;
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
