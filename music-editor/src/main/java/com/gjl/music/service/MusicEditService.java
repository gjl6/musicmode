package com.gjl.music.service;

import com.gjl.music.exception.MetadataWriteException;
import com.gjl.music.model.*;
import com.gjl.music.module.song.enrich.module.EnrichPipeline;
import com.gjl.music.module.song.filesystem.FileSystemModule;
import com.gjl.music.module.song.writer.WriterFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;


@Slf4j
@Service
public class MusicEditService {

    private final FileSystemModule fileSystem;
    private final WriterFactory writerFactory;
    private final EnrichPipeline enrichPipeline;
    private final String musicRootDir;

    public MusicEditService(FileSystemModule fileSystem,
                            WriterFactory writerFactory,
                            EnrichPipeline enrichPipeline,
                            @Value("${music.root-dir}") String musicRootDir) {
        this.fileSystem = fileSystem;
        this.writerFactory = writerFactory;
        this.enrichPipeline = enrichPipeline;
        this.musicRootDir = musicRootDir;
    }


    public MusicMetadata saveFields(String rawPath, Map<String, Object> metaMap) {
        String path = decode(rawPath);
        File root = new File(musicRootDir);
        File file = fileSystem.resolveFile(root, path);

        if (!file.exists() || !file.isFile()) {
            throw new IllegalArgumentException("文件不存在: " + path);
        }

        MusicMetadata editedMeta = buildMetadata(metaMap);
        enrichPipeline.resolveCovers(editedMeta);

        try {
            writerFactory.write(file, editedMeta);
        } catch (MetadataWriteException e) {
            throw new RuntimeException("写入失败: " + path, e);
        }

        return editedMeta;
    }

    @SuppressWarnings("unchecked")
    private MusicMetadata buildMetadata(Map<String, Object> metaMap) {
        MusicMetadata meta = new MusicMetadata();

                if (metaMap.get("songs") instanceof List<?> songs && !songs.isEmpty()
                && songs.get(0) instanceof Map<?, ?> s) {
            Song.SongBuilder<?, ?> sb = Song.builder();
            if (s.get("title") instanceof String t) sb.title(t);
            if (s.get("year") instanceof String y) sb.year(y);
            if (s.get("language") instanceof String l) sb.language(l);
            if (s.get("composer") instanceof String c) sb.composer(c);
            if (s.get("lyricist") instanceof String l) sb.lyricist(l);
            if (s.get("trackNumber") instanceof Number n) sb.trackNumber(n.intValue());
            if (s.get("discNumber") instanceof Number n) sb.discNumber(n.intValue());
            if (s.get("coverPath") instanceof String c) sb.coverPath(c);
            meta.addSong(sb.build());
        }

                if (metaMap.get("artists") instanceof List<?> artistList) {
            for (Object item : artistList) {
                if (item instanceof Map<?, ?> a) {
                    Artist.ArtistBuilder<?, ?> ab = Artist.builder();
                    if (a.get("artistName") instanceof String n) ab.artistName(n);
                    if (a.get("country") instanceof String c) ab.country(c);
                    if (a.get("gender") instanceof Number g) ab.gender(g.intValue());
                    if (a.get("introduction") instanceof String i) ab.introduction(i);
                    if (a.get("artistCover") instanceof String c) ab.artistCover(c);
                    meta.addArtist(ab.build());
                }
            }
        }

                if (metaMap.get("albums") instanceof List<?> albumList && !albumList.isEmpty()
                && albumList.get(0) instanceof Map<?, ?> a) {
            Album.AlbumBuilder<?, ?> ab = Album.builder();
            if (a.get("albumName") instanceof String n) ab.albumName(n);
            if (a.get("albumType") instanceof String t) {
                try { ab.albumType(AlbumType.valueOf(t)); } catch (IllegalArgumentException ignored) {}
            }
            if (a.get("albumYear") instanceof Number y) ab.albumYear(y.intValue());
            if (a.get("company") instanceof String c) ab.company(c);
            if (a.get("introduction") instanceof String i) ab.introduction(i);
            if (a.get("language") instanceof String l) ab.language(l);
            meta.addAlbum(ab.build());
        }

                if (metaMap.get("lyrics") instanceof List<?> lyricList && !lyricList.isEmpty()
                && lyricList.get(0) instanceof Map<?, ?> l) {
            Lyric.LyricBuilder<?, ?> lb = Lyric.builder();
            if (l.get("content") instanceof String c) lb.content(c);
            if (l.get("type") instanceof String t) lb.type(LyricType.valueOf(t));
            if (l.get("lrcPath") instanceof String p) lb.lrcPath(p);
            meta.addLyric(lb.build());
        }

                if (metaMap.get("styles") instanceof List<?> styleList && !styleList.isEmpty()
                && styleList.get(0) instanceof Map<?, ?> st) {
            Style.StyleBuilder<?, ?> sb = Style.builder();
            if (st.get("styleName") instanceof String n) sb.styleName(n);
            if (st.get("description") instanceof String d) sb.description(d);
            meta.addStyle(sb.build());
        }

        return meta;
    }

    private static String decode(String raw) {
        return URLDecoder.decode(raw != null ? raw : "", StandardCharsets.UTF_8);
    }

    @Value("${music.covers-dir:../covers}")
    private String coversDir;

    public String downloadCover(String url) {
        try {
            File dir = new File(coversDir);
            if (!dir.exists() && !dir.mkdirs()) {
                throw new RuntimeException("无法创建封面目录: " + coversDir);
            }

            URI uri = URI.create(url);
            byte[] data;
            try (InputStream in = uri.toURL().openStream()) {
                data = in.readAllBytes();
            }

            String hash = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(data)).substring(0, 16);

            String ext = "jpg";
            String lower = url.toLowerCase();
            if (lower.contains(".png")) ext = "png";
            else if (lower.contains(".gif")) ext = "gif";
            else if (lower.contains(".webp")) ext = "webp";

                        File out = new File(dir, hash + "." + ext);
            if (!out.exists()) {
                try (FileOutputStream fos = new FileOutputStream(out)) {
                    fos.write(data);
                }
            }

            return out.getAbsolutePath();
        } catch (Exception e) {
            log.error("封面下载失败: {}", e.getMessage());
            throw new RuntimeException("封面下载失败: " + e.getMessage());
        }
    }
}
