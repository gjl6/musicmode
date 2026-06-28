package com.gjl.music.parser;

import com.gjl.music.model.Song;
import lombok.extern.slf4j.Slf4j;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagTextField;
import org.jaudiotagger.tag.id3.AbstractID3v2Tag;
import org.jaudiotagger.tag.wav.WavInfoTag;
import org.jaudiotagger.tag.wav.WavTag;

import java.io.File;
import java.util.*;

/**
 * WAV 专用解析器 —— 读取 RIFF INFO 中 FieldKey 未映射的字段，
 * 并兜底提取内嵌 ID3v2 标签中的 TXXX 自定义帧。
 */
@Slf4j
public class WavParser extends DefaultParser {

    @Override
    protected Song extractSong(File file, Tag tag, AudioHeader header, String fileName, String filePath) {
        Song.SongBuilder<?, ?> builder = super.extractSong(file, tag, header, fileName, filePath).toBuilder();

        if (tag instanceof WavTag wavTag) {
            Map<String, String> extra = new LinkedHashMap<>();

            // 1. RIFF INFO 未映射字段（IENG, IARL, IKEY, ITGL 等）
            WavInfoTag infoTag = wavTag.getInfoTag();
            if (infoTag != null) {
                List<TagTextField> unrecognised = infoTag.getUnrecognisedFields();
                for (TagTextField f : unrecognised) {
                    String key = f.getId();
                    String val = f.getContent();
                    if (key != null && val != null && !val.isBlank()) {
                        extra.putIfAbsent(key, val.trim());
                    }
                }
            }

            // 2. 内嵌 ID3v2 标签的 TXXX 帧
            AbstractID3v2Tag id3v2 = wavTag.getID3Tag();
            if (id3v2 != null && !id3v2.isEmpty()) {
                Map<String, String> txxx = extractTxxxMap(id3v2);
                for (Map.Entry<String, String> e : txxx.entrySet()) {
                    extra.putIfAbsent(e.getKey(), e.getValue());
                }
                // ARRANGER / PRODUCER 映射
                if (txxx.containsKey("ARRANGER") && builder.build().getArranger() == null) {
                    builder.arranger(txxx.get("ARRANGER"));
                }
                if (txxx.containsKey("PRODUCER") && builder.build().getProducer() == null) {
                    builder.producer(txxx.get("PRODUCER"));
                }
                // Acoustid 指纹
                String acoustid = txxx.get("Acoustid Fingerprint");
                if (acoustid == null) acoustid = txxx.get("Acoustid Id");
                if (acoustid != null && builder.build().getFingerprint() == null) {
                    builder.fingerprint(acoustid);
                }
            }

            // 3. RIFF INFO 的 IENG（Engineer）兜底映射
            if (builder.build().getArranger() == null) {
                String ieng = extra.get("IENG");
                if (ieng != null) builder.arranger(ieng);
            }

            if (!extra.isEmpty()) builder.extraTags(toJson(extra));
        }

        return builder.build();
    }
}
