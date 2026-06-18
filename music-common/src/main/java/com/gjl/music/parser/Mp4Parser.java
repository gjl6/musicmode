package com.gjl.music.parser;

import com.gjl.music.exception.MetadataParseException;
import com.gjl.music.model.*;
import lombok.extern.slf4j.Slf4j;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.audio.exceptions.CannotReadVideoException;
import org.jaudiotagger.audio.generic.GenericAudioHeader;
import org.jaudiotagger.audio.mp4.Mp4InfoReader;
import org.jaudiotagger.audio.mp4.Mp4TagReader;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagField;
import org.jaudiotagger.tag.TagTextField;
import org.jaudiotagger.tag.mp4.Mp4Tag;
import org.jaudiotagger.tag.mp4.field.Mp4TagReverseDnsField;

import java.io.File;
import java.nio.file.Path;
import java.util.*;


@Slf4j
public class Mp4Parser extends DefaultParser {


    private static final Set<String> HANDLED_ATOM_IDS = Set.of(
            "©nam", "©ART", "©alb", "©day", "gnre", "©gen",
            "©lyr", "©cmt", "©wrt", "trkn", "disk", "cpil"
    );


    private static final Set<String> HANDLED_RDNS = Set.of(
            "LANGUAGE", "LYRICIST", "LABEL", "RECORD_LABEL",
            "IS_SOUNDTRACK", "IS_LIVE", "PERFORMER", "COUNTRY"
    );

    @Override
    public MusicMetadata parse(File file) throws MetadataParseException {
        validateFile(file);
        try {
            return super.parse(file);
        } catch (MetadataParseException e) {
            if (e.getCause() instanceof CannotReadVideoException) {
                log.info("MP4 被识别为视频，走降级路径: {}", file.getName());
                return parseVideo(file);
            }
            throw e;
        }
    }


    private MusicMetadata parseVideo(File file) throws MetadataParseException {
        try {
            Path path = file.toPath();
                        Mp4Tag tag = new Mp4TagReader().read(path);
                        AudioHeader header;
            try {
                header = new Mp4InfoReader().read(path);
            } catch (Exception e) {
                log.debug("MP4音频信息读取失败，从MVHD获取时长: {}", e.getMessage());
                header = buildFallbackHeader(path);
            }
                        String fileName = file.getName();
            String filePath = file.getAbsolutePath();
            MusicMetadata meta = new MusicMetadata();
            meta.addSong(extractSong(file, tag, header, fileName, filePath));
            Album album = extractAlbum(tag);
            if (album.getAlbumName() != null) meta.addAlbum(album);
            extractArtists(tag).forEach(meta::addArtist);
            Style style = extractStyle(tag);
            if (style.getStyleName() != null) meta.addStyle(style);
            extractLyrics(tag, filePath).forEach(meta::addLyric);
            return meta;
        } catch (MetadataParseException e) {
            throw e;
        } catch (Exception e) {
            log.error("MP4降级解析失败: {} - {}", file.getName(), e.getMessage());
            throw new MetadataParseException("解析元数据失败: " + file.getName(), e);
        }
    }

    @Override
    protected Song extractSong(File file, Tag tag, AudioHeader header, String fileName, String filePath) {
        Song.SongBuilder<?, ?> builder = super.extractSong(file, tag, header, fileName, filePath).toBuilder();

        if (tag instanceof Mp4Tag mp4Tag) {
            Map<String, String> extra = new LinkedHashMap<>();
            String arranger = null, producer = null, acoustid = null;

            Iterator<TagField> it = mp4Tag.getFields();
            while (it.hasNext()) {
                TagField f = it.next();
                String atomId = f.getId();

                                if (HANDLED_ATOM_IDS.contains(atomId)) continue;

                if (f instanceof Mp4TagReverseDnsField rdns) {
                    String identifier = rdns.getDescriptor();
                    if (identifier == null) continue;
                                        if (HANDLED_RDNS.contains(identifier.toUpperCase())) continue;

                    String val = rdns.getContent();
                    if (val == null || val.isBlank()) continue;
                    val = val.trim();

                    switch (identifier) {
                        case "ARRANGER" -> arranger = val;
                        case "PRODUCER" -> producer = val;
                        case "Acoustid Fingerprint" -> acoustid = val;
                        case "Acoustid Id" -> { if (acoustid == null) acoustid = val; }
                        default -> extra.putIfAbsent(identifier, val);
                    }
                } else if (f instanceof TagTextField tf) {
                    String val = tf.getContent();
                    if (val != null && !val.isBlank()) {
                        extra.putIfAbsent(atomId, val.trim());
                    }
                }
            }

                        if (arranger != null && builder.build().getArranger() == null) builder.arranger(arranger);
            if (producer != null && builder.build().getProducer() == null) builder.producer(producer);
            if (acoustid != null && builder.build().getFingerprint() == null) builder.fingerprint(acoustid);

                        if (builder.build().getArranger() == null) {
                String a = getFieldKeyValue(tag, FieldKey.ARRANGER);
                if (a != null) builder.arranger(a);
            }
            if (builder.build().getProducer() == null) {
                String p = getFieldKeyValue(tag, FieldKey.PRODUCER);
                if (p != null) builder.producer(p);
            }

            if (!extra.isEmpty()) builder.extraTags(toJson(extra));
        }

        return builder.build();
    }


    private static GenericAudioHeader buildFallbackHeader(Path path) {
        GenericAudioHeader h = new GenericAudioHeader();
        h.setPreciseLength(0);
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(path.toFile(), "r")) {
            long moovPos = findAtom(raf, "moov");
            if (moovPos >= 0) {
                raf.seek(moovPos + 8);
                long endPos = moovPos + raf.readInt();
                                raf.seek(moovPos + 8);
                                while (raf.getFilePointer() + 8 <= endPos && raf.getFilePointer() < raf.length()) {
                    int childSize = raf.readInt();
                    if (childSize <= 0) break;
                    byte[] childId = new byte[4];
                    raf.read(childId);
                    if ("mvhd".equals(new String(childId, java.nio.charset.StandardCharsets.US_ASCII))) {
                        int version = raf.read() & 0xFF;
                        raf.skipBytes(3);
                        if (version == 1) raf.skipBytes(16);
                        else raf.skipBytes(8);
                        int timescale = raf.readInt();
                        double dur = (version == 1) ? (double) raf.readLong()
                                : (double) (raf.readInt() & 0xFFFFFFFFL);
                        if (timescale > 0 && dur > 0) {
                            h.setPreciseLength(dur / timescale);
                            log.debug("MVHD时长: {}s", dur / timescale);
                        }
                        return h;
                    }
                    raf.skipBytes(childSize - 8);
                }
            }
        } catch (Exception e) {
            log.debug("MVHD读取失败: {}", e.getMessage());
        }
        return h;
    }


    private static long findAtom(java.io.RandomAccessFile raf, String atomId) throws Exception {
        byte[] target = atomId.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        raf.seek(0);
        long len = raf.length();
                if (len > 12) {
            raf.seek(4);
            byte[] id = new byte[4];
            raf.read(id);
            if ("ftyp".equals(new String(id, java.nio.charset.StandardCharsets.US_ASCII))) {
                raf.seek(0);
                int ftypSize = raf.readInt();
                raf.seek(ftypSize);
            } else {
                raf.seek(8);
            }
        }
                while (raf.getFilePointer() + 8 <= len) {
            long pos = raf.getFilePointer();
            int size = raf.readInt();
            if (size <= 0 || pos + size > len) break;
            byte[] id = new byte[4];
            raf.read(id);
            if (atomId.equals(new String(id, java.nio.charset.StandardCharsets.US_ASCII))) {
                raf.seek(pos);
                return pos;
            }
            if ("mdat".equals(new String(id, java.nio.charset.StandardCharsets.US_ASCII))) {
                                raf.seek(pos + size);
            }
        }
        return -1;
    }

    private String getFieldKeyValue(Tag tag, FieldKey key) {
        try {
            String v = tag.getFirst(key);
            return (v != null && !v.isBlank()) ? v.trim() : null;
        } catch (Exception e) { return null; }
    }
}
