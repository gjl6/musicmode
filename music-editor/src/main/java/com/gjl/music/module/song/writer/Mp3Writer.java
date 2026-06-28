package com.gjl.music.module.song.writer;

import com.gjl.music.model.MusicMetadata;
import com.gjl.music.model.Song;
import com.gjl.music.module.song.enrich.module.EnrichPipeline;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.id3.AbstractID3v2Tag;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * MP3 专用写入器 —— 在 ID3v2 标签中写入 FieldKey 未覆盖的 TXXX 自定义帧。
 */
class Mp3Writer extends DefaultWriter {

    /** extraTags 中已单独映射到 Song 明确字段的键，写回时排除 */
    private static final Set<String> MAPPED_TXXX_KEYS = Set.of(
            "ARRANGER", "PRODUCER", "Acoustid Fingerprint", "Acoustid Id"
    );

    Mp3Writer(EnrichPipeline enrichPipeline, String coversDir) {
        super(enrichPipeline, coversDir);
    }

    @Override
    protected void injectSong(Tag tag, MusicMetadata meta) {
        super.injectSong(tag, meta);

        if (tag instanceof AbstractID3v2Tag id3v2) {
            Song s = firstSong(meta).orElse(null);
            if (s == null) return;

            Map<String, String> replacements = new LinkedHashMap<>();
            if (s.getArranger() != null && !s.getArranger().isBlank())
                replacements.put("ARRANGER", s.getArranger());
            if (s.getProducer() != null && !s.getProducer().isBlank())
                replacements.put("PRODUCER", s.getProducer());
            if (s.getFingerprint() != null && !s.getFingerprint().isBlank())
                replacements.put("Acoustid Fingerprint", s.getFingerprint());
            replacements.put("Acoustid Id", null); // 删除旧的 Acoustid Id（用 Fingerprint 替代）

            // 合并 extraTags（排除已单独映射的键）
            Map<String, String> extra = parseJson(s.getExtraTags());
            for (Map.Entry<String, String> e : extra.entrySet()) {
                if (!MAPPED_TXXX_KEYS.contains(e.getKey())) {
                    replacements.putIfAbsent(e.getKey(), e.getValue());
                }
            }

            syncTxxxFrames(id3v2, replacements);
        }
    }
}
