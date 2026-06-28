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

/**
 * MP4/M4A 专用解析器 —— 读取 FieldKey 未覆盖的 iTunes/QuickTime 原子，
 * 补齐 ARRANGER/PRODUCER 映射。含视频 MP4 降级解析路径。
 */
@Slf4j
public class Mp4Parser extends DefaultParser {

    /** DefaultParser 已通过 FieldKey 读取的标准原子名，遍历时跳过。 */
    private static final Set<String> HANDLED_ATOM_IDS = Set.of(
            "©nam", "©ART", "©alb", "©day", "gnre", "©gen",
            "©lyr", "©cmt", "©wrt", "trkn", "disk", "cpil"
    );

    /** DefaultParser 已通过 FieldKey 读取的 reverse-DNS 字段标识符 */
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

    /** 视频 MP4 降级路径：Mp4TagReader 和 Mp4InfoReader 独立调用，绕过视频检测 */
    private MusicMetadata parseVideo(File file) throws MetadataParseException {
        try {
            Path path = file.toPath();
            // 1. 读标签（Mp4TagReader 无视频检测，只走 moov/udta/meta/ilst）
            Mp4Tag tag = new Mp4TagReader().read(path);
            // 2. 读音频信息（尝试标准路径，失败则用空头兜底）
            AudioHeader header;
            try {
                header = new Mp4InfoReader().read(path);
            } catch (Exception e) {
                log.debug("MP4音频信息读取失败，从MVHD获取时长: {}", e.getMessage());
                header = buildFallbackHeader(path);
            }
            // 3. 构造元数据
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

                // 标准原子已在 HANDLED_ATOM_IDS 中 → 跳过
                if (HANDLED_ATOM_IDS.contains(atomId)) continue;

                if (f instanceof Mp4TagReverseDnsField rdns) {
                    String identifier = rdns.getDescriptor();
                    if (identifier == null) continue;
                    // 已被 FieldKey 覆盖 → 跳过
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

            // 映射到 Song 明确字段
            if (arranger != null && builder.build().getArranger() == null) builder.arranger(arranger);
            if (producer != null && builder.build().getProducer() == null) builder.producer(producer);
            if (acoustid != null && builder.build().getFingerprint() == null) builder.fingerprint(acoustid);

            // 显式补齐：FieldKey.ARRANGER / FieldKey.PRODUCER（以防 reverse-DNS 未匹配到）
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

    /** 从文件直接读取 MVHD 原子获取时长 */
    private static GenericAudioHeader buildFallbackHeader(Path path) {
        GenericAudioHeader h = new GenericAudioHeader();
        h.setPreciseLength(0);
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(path.toFile(), "r")) {
            long moovPos = findAtom(raf, "moov");
            if (moovPos >= 0) {
                raf.seek(moovPos + 8); // skip moov header
                long endPos = moovPos + raf.readInt(); // moov size was stored at position 0
                // moov size already consumed by readInt, reposition
                raf.seek(moovPos + 8);
                // scan children of moov for mvhd
                while (raf.getFilePointer() + 8 <= endPos && raf.getFilePointer() < raf.length()) {
                    int childSize = raf.readInt();
                    if (childSize <= 0) break;
                    byte[] childId = new byte[4];
                    raf.read(childId);
                    if ("mvhd".equals(new String(childId, java.nio.charset.StandardCharsets.US_ASCII))) {
                        int version = raf.read() & 0xFF;
                        raf.skipBytes(3); // flags
                        if (version == 1) raf.skipBytes(16); // created + modified (8+8)
                        else raf.skipBytes(8); // created + modified (4+4)
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

    /** 在文件中查找指定四字符原子，返回其起始偏移，未找到返回 -1 */
    private static long findAtom(java.io.RandomAccessFile raf, String atomId) throws Exception {
        byte[] target = atomId.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        raf.seek(0);
        long len = raf.length();
        // 跳过第一个 ftyp 原子（长度在0-3字节）
        if (len > 12) {
            raf.seek(4);
            byte[] id = new byte[4];
            raf.read(id);
            if ("ftyp".equals(new String(id, java.nio.charset.StandardCharsets.US_ASCII))) {
                raf.seek(0);
                int ftypSize = raf.readInt();
                raf.seek(ftypSize); // 跳到 ftyp 之后
            } else {
                raf.seek(8); // 从头开始搜索
            }
        }
        // 按四字节边界扫描后续原子
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
                // mdat 很大，跳到它之后
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
