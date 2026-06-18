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


@Slf4j
public class WavParser extends DefaultParser {

    @Override
    protected Song extractSong(File file, Tag tag, AudioHeader header, String fileName, String filePath) {
        Song.SongBuilder<?, ?> builder = super.extractSong(file, tag, header, fileName, filePath).toBuilder();

        if (tag instanceof WavTag wavTag) {
            Map<String, String> extra = new LinkedHashMap<>();

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

                        AbstractID3v2Tag id3v2 = wavTag.getID3Tag();
            if (id3v2 != null && !id3v2.isEmpty()) {
                Map<String, String> txxx = extractTxxxMap(id3v2);
                for (Map.Entry<String, String> e : txxx.entrySet()) {
                    extra.putIfAbsent(e.getKey(), e.getValue());
                }
                                if (txxx.containsKey("ARRANGER") && builder.build().getArranger() == null) {
                    builder.arranger(txxx.get("ARRANGER"));
                }
                if (txxx.containsKey("PRODUCER") && builder.build().getProducer() == null) {
                    builder.producer(txxx.get("PRODUCER"));
                }
                                String acoustid = txxx.get("Acoustid Fingerprint");
                if (acoustid == null) acoustid = txxx.get("Acoustid Id");
                if (acoustid != null && builder.build().getFingerprint() == null) {
                    builder.fingerprint(acoustid);
                }
            }

                        if (builder.build().getArranger() == null) {
                String ieng = extra.get("IENG");
                if (ieng != null) builder.arranger(ieng);
            }

            if (!extra.isEmpty()) builder.extraTags(toJson(extra));
        }

        return builder.build();
    }
}
