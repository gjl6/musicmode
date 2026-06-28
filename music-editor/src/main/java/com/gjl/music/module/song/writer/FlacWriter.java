package com.gjl.music.module.song.writer;

import com.gjl.music.model.MusicMetadata;
import com.gjl.music.model.Song;
import com.gjl.music.module.song.enrich.module.EnrichPipeline;
import lombok.extern.slf4j.Slf4j;
import org.jaudiotagger.tag.FieldDataInvalidException;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.flac.FlacTag;
import org.jaudiotagger.tag.vorbiscomment.VorbisCommentTag;

import java.util.Map;
import java.util.Set;

/**
 * FLAC 专用写入器 —— 在 VorbisComment 中写入 FieldKey 未覆盖的自定义字段。
 */
@Slf4j
class FlacWriter extends DefaultWriter {

    private static final Set<String> MAPPED_VC_KEYS = Set.of(
            "ARRANGER", "PRODUCER", "ACOUSTID_FINGERPRINT", "ACOUSTID_ID"
    );

    FlacWriter(EnrichPipeline enrichPipeline, String coversDir) {
        super(enrichPipeline, coversDir);
    }

    @Override
    protected void injectSong(Tag tag, MusicMetadata meta) {
        super.injectSong(tag, meta);

        if (tag instanceof FlacTag flacTag) {
            VorbisCommentTag vc = flacTag.getVorbisCommentTag();
            Song s = firstSong(meta).orElse(null);
            if (s == null) return;

            setVcField(vc, "ARRANGER", s.getArranger());
            setVcField(vc, "PRODUCER", s.getProducer());
            setVcField(vc, "ACOUSTID_FINGERPRINT", s.getFingerprint());

            if (s.getExtraTags() != null && !s.getExtraTags().isBlank()) {
                Map<String, String> extra = parseJson(s.getExtraTags());
                for (Map.Entry<String, String> e : extra.entrySet()) {
                    if (!MAPPED_VC_KEYS.contains(e.getKey().toUpperCase())) {
                        setVcField(vc, e.getKey(), e.getValue());
                    }
                }
            }
        }
    }

    private void setVcField(VorbisCommentTag vc, String key, String value) {
        if (value == null || value.isBlank()) return;
        try { vc.setField(key, value); }
        catch (FieldDataInvalidException e) { log.warn("FLAC字段写入失败 [{}]: {}", key, e.getMessage()); }
    }
}
