package com.gjl.music.parser;

import com.gjl.music.infra.util.FileHashUtils;
import com.gjl.music.model.Lyric;
import com.gjl.music.model.LyricType;
import com.gjl.music.model.Song;
import lombok.extern.slf4j.Slf4j;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagField;
import org.jaudiotagger.tag.id3.AbstractID3v2Frame;
import org.jaudiotagger.tag.id3.AbstractID3v2Tag;
import org.jaudiotagger.tag.id3.framebody.*;

import java.io.File;
import java.util.*;


@Slf4j
public class Mp3Parser extends DefaultParser {

    @Override
    protected Song extractSong(File file, Tag tag, AudioHeader header, String fileName, String filePath) {
        Song.SongBuilder<?, ?> builder = super.extractSong(file, tag, header, fileName, filePath).toBuilder();

        if (tag instanceof AbstractID3v2Tag id3v2) {
            Map<String, String> txxx = extractTxxx(id3v2);
            if (!txxx.isEmpty()) {
                builder.extraTags(toJson(txxx));
                                mapTxxxToSong(txxx, builder);
            }
            extractIplsTipl(id3v2, builder);
            extractTsseTenc(id3v2, builder);
        }

        return builder.build();
    }

    @Override
    protected List<Lyric> extractLyrics(Tag tag, String filePath) {
        List<Lyric> lyrics = new ArrayList<>(super.extractLyrics(tag, filePath));

        if (tag instanceof AbstractID3v2Tag id3v2) {
                        List<TagField> usltFrames = id3v2.getFrame("USLT");
            if (usltFrames != null) {
                for (TagField f : usltFrames) {
                    try {
                        FrameBodyUSLT body = (FrameBodyUSLT) ((AbstractID3v2Frame) f).getBody();
                        String text = body.getLyric();
                        if (text != null && !text.isBlank()) {
                            lyrics.add(Lyric.builder().content(text).type(LyricType.METADATA)
                                    .lyricHash(FileHashUtils.getFileHash(text)).build());
                        }
                    } catch (Exception e) {
                        log.warn("USLT 解析失败: {}", e.getMessage());
                    }
                }
            }
                        List<TagField> syltFrames = id3v2.getFrame("SYLT");
            if (syltFrames != null) {
                for (TagField f : syltFrames) {
                    try {
                        FrameBodySYLT body = (FrameBodySYLT) ((AbstractID3v2Frame) f).getBody();
                        String text = formatSylt(body);
                        if (text != null && !text.isBlank()) {
                            lyrics.add(Lyric.builder().content(text).type(LyricType.METADATA)
                                    .lyricHash(FileHashUtils.getFileHash(text)).build());
                        }
                    } catch (Exception e) {
                        log.warn("SYLT 解析失败: {}", e.getMessage());
                    }
                }
            }
        }

        return lyrics;
    }


    private Map<String, String> extractTxxx(AbstractID3v2Tag id3v2) {
        Map<String, String> map = new LinkedHashMap<>();
        List<TagField> txxxFrames = id3v2.getFrame("TXXX");
        if (txxxFrames == null) return map;
        for (TagField f : txxxFrames) {
            try {
                FrameBodyTXXX body = (FrameBodyTXXX) ((AbstractID3v2Frame) f).getBody();
                String desc = body.getDescription();
                String value = body.getText();
                if (desc != null && !desc.isBlank() && value != null && !value.isBlank()) {
                    map.putIfAbsent(desc.trim(), value.trim());
                }
            } catch (Exception e) {
                log.warn("TXXX 解析失败: {}", e.getMessage());
            }
        }
        return map;
    }

    private void mapTxxxToSong(Map<String, String> txxx, Song.SongBuilder<?, ?> builder) {
        String arranger = txxx.get("ARRANGER");
        if (arranger != null) builder.arranger(arranger);
        String producer = txxx.get("PRODUCER");
        if (producer != null) builder.producer(producer);
                String acoustid = txxx.get("Acoustid Fingerprint");
        if (acoustid == null) acoustid = txxx.get("Acoustid Id");
        if (acoustid != null && builder.build().getFingerprint() == null) builder.fingerprint(acoustid);
    }


    private void extractIplsTipl(AbstractID3v2Tag id3v2, Song.SongBuilder<?, ?> builder) {
        Map<String, String> people = new LinkedHashMap<>();
                safeIterateFrame(id3v2, "TIPL", f -> {
            try {
                FrameBodyTIPL body = (FrameBodyTIPL) ((AbstractID3v2Frame) f).getBody();
                for (int i = 0; i < body.getNumberOfPairs(); i++) {
                    String key = body.getKeyAtIndex(i);
                    String val = body.getValueAtIndex(i);
                    if (key != null && val != null && !val.isBlank()) people.putIfAbsent(key, val);
                }
            } catch (Exception e) { log.warn("TIPL 解析失败: {}", e.getMessage()); }
        });
                safeIterateFrame(id3v2, "IPLS", f -> {
            try {
                FrameBodyIPLS body = (FrameBodyIPLS) ((AbstractID3v2Frame) f).getBody();
                for (int i = 0; i < body.getNumberOfPairs(); i++) {
                    String key = body.getKeyAtIndex(i);
                    String val = body.getValueAtIndex(i);
                    if (key != null && val != null && !val.isBlank()) people.putIfAbsent(key, val);
                }
            } catch (Exception e) { log.warn("IPLS 解析失败: {}", e.getMessage()); }
        });

        String arr = people.get("arranger");
        if (arr != null && builder.build().getArranger() == null) builder.arranger(arr);
        String prod = people.get("producer");
        if (prod != null && builder.build().getProducer() == null) builder.producer(prod);
    }


    private void extractTsseTenc(AbstractID3v2Tag id3v2, Song.SongBuilder<?, ?> builder) {
                String tsse = getFrameText(id3v2, "TSSE");
        String tenc = getFrameText(id3v2, "TENC");
        if (tsse != null || tenc != null) {
            Song current = builder.build();
            String existing = current.getExtraTags();
            Map<String, String> map = (existing != null && !existing.isEmpty())
                    ? parseJson(existing) : new LinkedHashMap<>();
            if (tsse != null) map.putIfAbsent("ENCODE_SETTINGS", tsse);
            if (tenc != null) map.putIfAbsent("ENCODED_BY", tenc);
            builder.extraTags(toJson(map));
        }
    }

    private void safeIterateFrame(AbstractID3v2Tag id3v2, String frameId,
                                   java.util.function.Consumer<TagField> consumer) {
        List<TagField> frames = id3v2.getFrame(frameId);
        if (frames == null) return;
        frames.forEach(consumer);
    }

    private String getFrameText(AbstractID3v2Tag id3v2, String frameId) {
        try {
            AbstractID3v2Frame frame = id3v2.getFirstField(frameId);
            if (frame != null) {
                String content = frame.getContent();
                return (content != null && !content.isBlank()) ? content : null;
            }
        } catch (Exception e) {  }
        return null;
    }


    private String formatSylt(FrameBodySYLT body) {
        try {
            byte[] data = body.getLyrics();
            if (data == null || data.length == 0) return null;
            String raw = new String(data, java.nio.charset.StandardCharsets.UTF_8);

                                    String filtered = raw.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "\n");
            String[] lines = filtered.split("\n");
            StringBuilder sb = new StringBuilder();
            for (String line : lines) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && trimmed.length() > 2) {
                    sb.append(trimmed).append("\n");
                }
            }
            return sb.isEmpty() ? null : sb.toString().stripTrailing();
        } catch (Exception e) {
            log.warn("SYLT 格式化失败: {}", e.getMessage());
            return null;
        }
    }

}
