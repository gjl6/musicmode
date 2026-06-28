package com.gjl.music.module.song.writer;

import com.gjl.music.exception.MetadataWriteException;
import com.gjl.music.model.MusicMetadata;
import com.gjl.music.model.Song;
import com.gjl.music.module.song.enrich.module.EnrichPipeline;
import lombok.extern.slf4j.Slf4j;
import org.jaudiotagger.audio.exceptions.CannotReadVideoException;
import org.jaudiotagger.audio.mp4.Mp4TagReader;
import org.jaudiotagger.audio.mp4.Mp4TagWriter;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.mp4.Mp4FieldKey;
import org.jaudiotagger.tag.mp4.Mp4Tag;

import java.io.File;
import java.util.Map;
import java.util.Set;

/**
 * MP4/M4A 专用写入器 —— 写入 QuickTime 反向 DNS 原子中 FieldKey 未覆盖的字段。
 *
 * <p>视频 MP4 文件走降级路径：Mp4TagReader（无视频检测）读取标签，
 * 修改后通过 Mp4TagWriter 写回，绕过 AudioFileIO.read() 的视频拒绝。</p>
 */
@Slf4j
class Mp4Writer extends DefaultWriter {

    private static final Set<String> MAPPED_MP4_KEYS = Set.of(
            "ARRANGER", "PRODUCER", "Acoustid Fingerprint", "Acoustid Id"
    );

    Mp4Writer(EnrichPipeline enrichPipeline, String coversDir) {
        super(enrichPipeline, coversDir);
    }

    @Override
    public void write(File file, MusicMetadata metadata) throws MetadataWriteException {
        try {
            super.write(file, metadata);
        } catch (MetadataWriteException e) {
            if (e.getCause() instanceof CannotReadVideoException) {
                log.info("MP4 被识别为视频，走降级写入路径: {}", file.getName());
                writeVideo(file, metadata);
            } else {
                throw e;
            }
        }
    }

    /** 视频 MP4 降级写入：Mp4TagReader + Mp4TagWriter 绕过视频检测 */
    private void writeVideo(File file, MusicMetadata metadata) throws MetadataWriteException {
        try {
            Mp4Tag tag = new Mp4TagReader().read(file.toPath());
            injectCommon(tag, metadata);
            new Mp4TagWriter("mp4").write(tag, file.toPath());
            log.debug("MP4 视频降级写入完成: {}", file.getName());
        } catch (Exception e) {
            log.error("MP4 视频降级写入失败: {} - {}", file.getName(), e.getMessage());
            throw new MetadataWriteException("MP4视频写入失败: " + file.getName(), e);
        }
    }

    @Override
    protected void injectSong(Tag tag, MusicMetadata meta) {
        super.injectSong(tag, meta);

        if (tag instanceof Mp4Tag mp4Tag) {
            Song s = firstSong(meta).orElse(null);
            if (s == null) return;

            writeMp4Field(mp4Tag, "ARRANGER", s.getArranger());
            writeMp4Field(mp4Tag, "PRODUCER", s.getProducer());

            if (s.getFingerprint() != null && !s.getFingerprint().isBlank()) {
                writeMp4Field(mp4Tag, "Acoustid Fingerprint", s.getFingerprint());
            }

            if (s.getExtraTags() != null && !s.getExtraTags().isBlank()) {
                Map<String, String> extra = parseJson(s.getExtraTags());
                for (Map.Entry<String, String> e : extra.entrySet()) {
                    if (MAPPED_MP4_KEYS.contains(e.getKey())) continue;
                    writeMp4Field(mp4Tag, e.getKey(), e.getValue());
                }
            }
        }
    }

    /** 在 Mp4FieldKey 枚举中查找并写入字段 */
    private void writeMp4Field(Mp4Tag mp4Tag, String identifier, String value) {
        if (value == null || value.isBlank()) return;
        Mp4FieldKey key = findMp4FieldKey(identifier);
        if (key == null) {
            log.debug("MP4 无对应 Mp4FieldKey，跳过写入: {}", identifier);
            return;
        }
        try {
            mp4Tag.deleteField(key);
            mp4Tag.setField(key, value);
        } catch (Exception e) {
            log.warn("MP4 字段写入失败 [{}]: {}", identifier, e.getMessage());
        }
    }

    /** 按 identifier / atomId 在 Mp4FieldKey 枚举中查找 */
    private static Mp4FieldKey findMp4FieldKey(String identifier) {
        if (identifier == null || identifier.isBlank()) return null;
        try { return Mp4FieldKey.valueOf(identifier.toUpperCase()); } catch (Exception ignored) {}
        try { return Mp4FieldKey.valueOf(identifier); } catch (Exception ignored) {}
        return null;
    }
}
