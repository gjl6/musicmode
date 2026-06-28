package com.gjl.music.module.song.writer;

import com.gjl.music.exception.InvalidFileException;
import com.gjl.music.exception.MetadataWriteException;
import com.gjl.music.model.*;
import com.gjl.music.module.song.enrich.module.EnrichPipeline;
import lombok.extern.slf4j.Slf4j;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldDataInvalidException;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagField;
import org.jaudiotagger.tag.id3.AbstractID3v2Frame;
import org.jaudiotagger.tag.id3.AbstractID3v2Tag;
import org.jaudiotagger.tag.id3.ID3v24Frame;
import org.jaudiotagger.tag.id3.framebody.FrameBodyTXXX;
import org.jaudiotagger.tag.images.Artwork;
import org.jaudiotagger.tag.images.ArtworkFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 默认元数据写入器 —— 所有格式的公共写入逻辑。
 *
 * <p>子类覆盖 {@link #prepare(AudioFile, File, MusicMetadata)} 和
 * {@link #finish(File, MusicMetadata)} 注入格式特定行为。
 * {@link WriterFactoryImpl} 负责根据扩展名分派。</p>
 */
@Slf4j
public class DefaultWriter {

    private final EnrichPipeline enrichPipeline;
    protected final String coversDir;

    public DefaultWriter(EnrichPipeline enrichPipeline, String coversDir) {
        this.enrichPipeline = enrichPipeline;
        this.coversDir = coversDir;
    }

    public void write(File file, MusicMetadata metadata) throws MetadataWriteException {
        validateFile(file);
        enrichPipeline.resolveCovers(metadata);  // URL → 本地路径
        try {
            AudioFile af = AudioFileIO.read(file);
            prepare(af, file, metadata);                    // 子类钩子：格式特定准备
            Tag tag = af.getTagOrCreateAndSetDefault();
            injectCommon(tag, metadata);
            af.commit();
            // commit 后文件 mtime 已变，同步更新到 Song 对象，确保后续 DB 持久化写入正确值
            metadata.getImmutableSongs().forEach(s -> s.setFileMtime(file.lastModified()));
            finish(file, metadata);                         // 子类钩子：格式特定收尾
        } catch (MetadataWriteException e) {
            throw e;
        } catch (Exception e) {
            log.error("写入元数据失败: {} - {}", file.getName(), e.getMessage());
            throw new MetadataWriteException("写入元数据失败: " + file.getName(), e);
        }
    }

    /** 格式特定准备 —— 子类覆盖（如 WAV 设置 ID3+WavInfoTag） */
    protected void prepare(AudioFile af, File file, MusicMetadata meta) throws Exception {}

    /** 格式特定收尾 —— 子类覆盖（如 WAV 写入 INFO chunk 到文件） */
    protected void finish(File file, MusicMetadata meta) throws Exception {}

    // ═══════════════════════════════════════════════════════════════
    // 公共注入逻辑
    // ═══════════════════════════════════════════════════════════════

    protected void injectCommon(Tag tag, MusicMetadata meta) throws IOException {
        injectSong(tag, meta);
        injectAlbum(tag, meta);
        injectArtist(tag, meta);
        injectStyle(tag, meta);
        injectLyric(tag, meta);
        injectCover(tag, meta);
    }

    protected void injectSong(Tag tag, MusicMetadata meta) {
        firstSong(meta).ifPresent(s -> {
            setField(tag, FieldKey.TITLE, s.getTitle());
            setField(tag, FieldKey.YEAR, s.getYear());
            setField(tag, FieldKey.LANGUAGE, s.getLanguage());
            setField(tag, FieldKey.COMPOSER, s.getComposer());
            setField(tag, FieldKey.LYRICIST, s.getLyricist());
            if (s.getTrackNumber() != null) setField(tag, FieldKey.TRACK, s.getTrackNumber().toString());
            if (s.getDiscNumber() != null) setField(tag, FieldKey.DISC_NO, s.getDiscNumber().toString());
            try { tag.setField(FieldKey.ENCODER, "PCM"); } catch (FieldDataInvalidException ignored) {}
        });
    }

    protected void injectAlbum(Tag tag, MusicMetadata meta) {
        meta.getImmutableAlbums().stream().findFirst()
                .ifPresent(a -> {
                    setField(tag, FieldKey.ALBUM, a.getAlbumName());
                    if (a.getAlbumYear() != null) setField(tag, FieldKey.ALBUM_YEAR, a.getAlbumYear().toString());
                    setField(tag, FieldKey.COMMENT, a.getIntroduction());
                    setField(tag, FieldKey.RECORD_LABEL, a.getCompany());
                    setField(tag, FieldKey.LANGUAGE, a.getLanguage());
                    if (a.getAlbumType() != null) {
                        switch (a.getAlbumType()) {
                            case COMPILATION -> setField(tag, FieldKey.IS_COMPILATION, "1");
                            case SOUNDTRACK -> setField(tag, FieldKey.IS_SOUNDTRACK, "1");
                            case EP -> setField(tag, FieldKey.IS_LIVE, "1"); // EP → live flag 最近似
                        }
                    }
                });
    }

    protected void injectArtist(Tag tag, MusicMetadata meta) {
        List<Artist> artists = meta.getImmutableArtists();
        if (artists.isEmpty()) return;

        // 逐艺术家写入，jaudiotagger 自动处理多值分隔
        tag.deleteField(FieldKey.ARTIST);
        for (Artist a : artists) {
            if (a.getArtistName() != null && !a.getArtistName().isBlank()) {
                try { tag.addField(FieldKey.ARTIST, a.getArtistName()); }
                catch (FieldDataInvalidException e) { log.warn("写入ARTIST失败: {}", e.getMessage()); }
            }
        }

        artists.stream().findFirst()
                .ifPresent(a -> setField(tag, FieldKey.COUNTRY, a.getCountry()));
    }

    protected void injectStyle(Tag tag, MusicMetadata meta) {
        meta.getImmutableStyles().stream().findFirst()
                .ifPresent(s -> setField(tag, FieldKey.GENRE, s.getStyleName()));
    }

    protected void injectLyric(Tag tag, MusicMetadata meta) {
        meta.getImmutableLyrics().stream()
                .filter(l -> l.getType() == LyricType.METADATA).findFirst()
                .ifPresent(l -> setField(tag, FieldKey.LYRICS, l.getContent()));
    }

    protected Optional<Song> firstSong(MusicMetadata meta) {
        return meta.getImmutableSongs().stream().findFirst();
    }

    protected void injectCover(Tag tag, MusicMetadata meta) throws IOException {
        Optional<Song> song = firstSong(meta);
        if (song.isEmpty()) return;
        String coverPath = song.get().getCoverPath();
        if (coverPath == null || coverPath.trim().isEmpty()) return;
        // 相对路径基于 coversDir 解析，绝对路径直接使用
        Path p = Paths.get(coverPath);
        if (!p.isAbsolute()) {
            p = Path.of(coversDir).resolve(coverPath);
        }
        if (!Files.exists(p)) throw new FileNotFoundException("封面文件未找到: " + coverPath);
        byte[] data = Files.readAllBytes(p);
        if (data.length == 0) return;
        tag.deleteArtworkField();
        Artwork aw = ArtworkFactory.getNew();
        aw.setPictureType(3);
        aw.setMimeType(mimeType(p));
        aw.setBinaryData(data);
        try { tag.setField(aw); } catch (FieldDataInvalidException e) { log.warn("设置封面失败: {}", e.getMessage()); }
    }

    // ═══════════════════════════════════════════════════════════════
    // 辅助
    // ═══════════════════════════════════════════════════════════════

    protected final void validateFile(File f) {
        if (!f.exists()) throw new InvalidFileException("文件不存在: " + f.getAbsolutePath());
        if (f.length() < 10) throw new InvalidFileException("文件太小: " + f.getAbsolutePath());
    }

    protected void setField(Tag tag, FieldKey key, String value) {
        if (value != null && !value.isEmpty()) {
            try { tag.deleteField(key); tag.setField(key, value); }
            catch (FieldDataInvalidException e) { log.warn("设置字段失败 [{}]: {}", key, e.getMessage()); }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // ID3v2 TXXX 写入辅助（Mp3Writer / WavWriter 共用）
    // ═══════════════════════════════════════════════════════════════

    /**
     * 同步 ID3v2 TXXX 帧：保留已有帧中不在 replacements 中的，写入 replacements。
     * @param id3v2    ID3v2 标签
     * @param replacements  description→value 映射；value 为 null 或空白表示删除该键
     */
    protected static void syncTxxxFrames(AbstractID3v2Tag id3v2, Map<String, String> replacements) {
        // 1. 读取已有 TXXX，排除将在 replacements 中覆写的
        Map<String, String> toKeep = new LinkedHashMap<>();
        List<TagField> existing = id3v2.getFields("TXXX");
        if (existing != null) {
            for (TagField f : existing) {
                try {
                    FrameBodyTXXX body = (FrameBodyTXXX) ((AbstractID3v2Frame) f).getBody();
                    String desc = body.getDescription();
                    if (desc != null && !replacements.containsKey(desc)) {
                        toKeep.putIfAbsent(desc, body.getText());
                    }
                } catch (Exception ignored) {}
            }
        }
        // 2. 删除全部 TXXX
        id3v2.removeFrame("TXXX");
        // 3. 写回保留的
        toKeep.forEach((k, v) -> addSingleTxxx(id3v2, k, v));
        // 4. 写入新值
        replacements.forEach((k, v) -> {
            if (v != null && !v.isBlank()) addSingleTxxx(id3v2, k, v);
        });
    }

    private static void addSingleTxxx(AbstractID3v2Tag id3v2, String desc, String value) {
        FrameBodyTXXX body = new FrameBodyTXXX();
        body.setDescription(desc);
        body.setText(value);
        ID3v24Frame frame = new ID3v24Frame("TXXX");
        frame.setBody(body);
        try { id3v2.addField(frame); } catch (Exception e) { log.warn("TXXX写入失败[{}]: {}", desc, e.getMessage()); }
    }

    // ═══════════════════════════════════════════════════════════════
    // 简单 JSON 编解码（从 DefaultParser 复制，避免跨包依赖）
    // ═══════════════════════════════════════════════════════════════

    protected static Map<String, String> parseJson(String json) {
        Map<String, String> map = new LinkedHashMap<>();
        if (json == null || json.isBlank()) return map;
        String content = json.strip();
        if (!content.startsWith("{") || !content.endsWith("}")) return map;
        content = content.substring(1, content.length() - 1);
        for (String pair : content.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)")) {
            int colon = pair.indexOf(':');
            if (colon < 0) continue;
            String key = pair.substring(0, colon).strip();
            String value = pair.substring(colon + 1).strip();
            if (key.startsWith("\"") && key.endsWith("\"")) key = key.substring(1, key.length() - 1);
            if (value.startsWith("\"") && value.endsWith("\"")) value = value.substring(1, value.length() - 1);
            if (!key.isEmpty() && !value.isEmpty()) map.putIfAbsent(key, value);
        }
        return map;
    }

    private String mimeType(Path p) {
        String n = p.getFileName().toString().toLowerCase();
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".bmp")) return "image/bmp";
        return "image/jpeg";
    }
}
