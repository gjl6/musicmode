package com.gjl.music.module.song.support;

import com.gjl.music.editor.mapper.SongManageMapper;
import com.gjl.music.model.*;

import java.util.*;

/**
 * 元数据模块便捷基类 —— 预设 METADATA_PARSE 补缺 + 全量 DB 缓存重建。
 *
 * <p>查询 song + album + artist + style + lyric 全部关联表，
 * 从 DB 行重建完整 MusicMetadata（含 Artists、Albums、Styles、Lyrics）。</p>
 */
public abstract class MetadataGapFillingModule extends GapFillingModule {

    protected MetadataGapFillingModule(SongManageMapper songManageMapper) {
        super(songManageMapper);
    }

    @Override
    public ExecutorType executorType() { return ExecutorType.VIRTUAL; }

    @Override
    protected String fillModuleName() {
        return "parser";
    }

    @Override
    protected List<Map<String, Object>> queryDbCache(List<String> paths) {
        return songManageMapper.findFullMetadataByPaths(paths);
    }

    @Override
    protected boolean isDataMissing(Map<String, Object> row) {
        return row == null;
    }

    /**
     * 从 invoke 返回的 outputs 中提取单个文件的 MusicMetadata。
     * invoke 返回格式为 {@code {"node.parser.output": Map<Path, MusicMetadata>}}，
     * 需要展开内层 Map 查找匹配 filePath 的条目。
     */
    @Override
    @SuppressWarnings("unchecked")
    protected MusicMetadata extractForkResult(Object forkResult, String filePath) {
        if (forkResult instanceof Map<?,?> outputs) {
            // 遍历 outputs 的每个 value，可能是内层 Map<Path, Metadata>
            for (Object value : outputs.values()) {
                if (value instanceof Map<?,?> innerMap) {
                    for (var inner : innerMap.entrySet()) {
                        if (filePath.equals(inner.getKey().toString())
                                && inner.getValue() instanceof MusicMetadata meta) {
                            return meta;
                        }
                    }
                } else if (value instanceof MusicMetadata meta) {
                    return meta; // 单文件结果直接返回
                }
            }
        }
        return null;
    }

    @Override
    protected MusicMetadata rowToMetadata(Map<String, Object> row, String filePath) {
        MusicMetadata meta = new MusicMetadata();

        // ── Song ──
        Song.SongBuilder sb = Song.builder().filePath(filePath);
        sb.title(str(row, "TITLE"));
        sb.fileName(str(row, "FILE_NAME"));
        sb.fileFormat(str(row, "FILE_FORMAT"));
        sb.fileSize(num(row, "FILE_SIZE"));
        sb.fileHash(str(row, "FILE_HASH"));
        sb.fingerprint(str(row, "FINGERPRINT"));
        sb.duration(num(row, "DURATION"));
        sb.coverPath(str(row, "COVER_PATH"));
        sb.year(str(row, "YEAR"));
        sb.bitrate(num(row, "BITRATE"));
        sb.sampleRate(num(row, "SAMPLE_RATE"));
        sb.channels(num(row, "CHANNELS"));
        sb.bitsPerSample(num(row, "BITS_PER_SAMPLE"));
        sb.language(str(row, "LANGUAGE"));
        sb.trackNumber(num(row, "TRACK_NUMBER"));
        sb.discNumber(num(row, "DISC_NUMBER"));
        sb.trackGain(dbl(row, "TRACK_GAIN"));
        sb.trackPeak(dbl(row, "TRACK_PEAK"));
        sb.composer(str(row, "COMPOSER"));
        sb.lyricist(str(row, "LYRICIST"));
        sb.arranger(str(row, "ARRANGER"));
        sb.producer(str(row, "PRODUCER"));
        sb.fileMtime(numLong(row, "FILE_MTIME"));
        Object albumId = row.get("ALBUM_ID");
        if (albumId instanceof Number n) sb.albumId(n.intValue());
        meta.addSong(sb.build());

        // ── Album ──
        if (!isBlank(str(row, "ALBUM_NAME"))) {
            Album.AlbumBuilder ab = Album.builder().albumName(str(row, "ALBUM_NAME"));
            ab.albumYear(num(row, "ALBUM_YEAR"));
            ab.introduction(str(row, "ALBUM_INTRODUCTION"));
            ab.company(str(row, "ALBUM_COMPANY"));
            ab.language(str(row, "ALBUM_LANGUAGE"));
            meta.addAlbum(ab.build());
        }

        // ── Artists (name::country||name::country) ──
        String artistData = str(row, "ARTIST_DATA");
        if (!isBlank(artistData)) {
            for (String part : artistData.split("\\|\\|")) {
                String[] pair = part.split("::", 2);
                if (!pair[0].isBlank()) {
                    Artist.ArtistBuilder ab2 = Artist.builder().artistName(pair[0]);
                    if (pair.length > 1 && !pair[1].isBlank()) ab2.country(pair[1]);
                    meta.addArtist(ab2.build());
                }
            }
        }

        // ── Styles ──
        String styleNames = str(row, "STYLE_NAMES");
        if (!isBlank(styleNames)) {
            for (String name : styleNames.split("\\|\\|")) {
                if (!name.isBlank()) {
                    meta.addStyle(Style.builder().styleName(name).build());
                }
            }
        }

        // ── Lyrics ──
        String lyricContents = str(row, "LYRIC_CONTENTS");
        if (!isBlank(lyricContents)) {
            for (String content : lyricContents.split("\\|\\|\\|")) {
                if (!content.isBlank()) {
                    meta.addLyric(Lyric.builder().content(content).build());
                }
            }
        }

        return meta;
    }

    private static String str(Map<String, Object> row, String col) {
        Object v = row.get(col);
        return v != null ? v.toString() : null;
    }

    private static Integer num(Map<String, Object> row, String col) {
        Object v = row.get(col);
        return v instanceof Number n ? n.intValue() : null;
    }

    private static Long numLong(Map<String, Object> row, String col) {
        Object v = row.get(col);
        return v instanceof Number n ? n.longValue() : null;
    }

    private static Double dbl(Map<String, Object> row, String col) {
        Object v = row.get(col);
        return v instanceof Number n ? n.doubleValue() : null;
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
}
