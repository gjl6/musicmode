package com.gjl.music.module.song.writer;

import com.gjl.music.model.*;
import com.gjl.music.module.song.enrich.module.EnrichPipeline;
import lombok.extern.slf4j.Slf4j;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.wav.WavOptions;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.id3.AbstractID3v2Tag;
import org.jaudiotagger.tag.id3.ID3v24Tag;
import org.jaudiotagger.tag.wav.WavInfoTag;
import org.jaudiotagger.tag.wav.WavTag;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

/** WAV 专属写入器 —— 写入 ID3 tag + Windows 兼容的 LIST INFO chunk（GBK 编码） */
@Slf4j
class WavWriter extends DefaultWriter {

    private static final Charset GBK = Charset.forName("GBK");

    WavWriter(EnrichPipeline enrichPipeline, String coversDir) {
        super(enrichPipeline, coversDir);
    }

    @Override
    protected void prepare(AudioFile af, File file, MusicMetadata meta) {
        Tag existing = af.getTag();
        if (existing instanceof WavTag wav) {
            if (wav.getID3Tag() == null) wav.setID3Tag(new ID3v24Tag());
            if (wav.getInfoTag() == null) wav.setInfoTag(new WavInfoTag());
        } else {
            WavTag wavTag = new WavTag(WavOptions.READ_ID3_ONLY);
            wavTag.setID3Tag(new ID3v24Tag());
            wavTag.setInfoTag(new WavInfoTag());
            af.setTag(wavTag);
        }
    }

    private static final Set<String> MAPPED_TXXX_KEYS = Set.of(
            "ARRANGER", "PRODUCER", "Acoustid Fingerprint", "Acoustid Id"
    );

    @Override
    protected void injectSong(Tag tag, MusicMetadata meta) {
        super.injectSong(tag, meta);

        if (tag instanceof WavTag wavTag) {
            AbstractID3v2Tag id3v2 = wavTag.getID3Tag();
            Song s = firstSong(meta).orElse(null);
            if (s == null) return;

            Map<String, String> replacements = new LinkedHashMap<>();
            if (s.getArranger() != null && !s.getArranger().isBlank())
                replacements.put("ARRANGER", s.getArranger());
            if (s.getProducer() != null && !s.getProducer().isBlank())
                replacements.put("PRODUCER", s.getProducer());
            if (s.getFingerprint() != null && !s.getFingerprint().isBlank())
                replacements.put("Acoustid Fingerprint", s.getFingerprint());
            replacements.put("Acoustid Id", null);

            Map<String, String> extra = parseJson(s.getExtraTags());
            for (Map.Entry<String, String> e : extra.entrySet()) {
                if (!MAPPED_TXXX_KEYS.contains(e.getKey())) {
                    replacements.putIfAbsent(e.getKey(), e.getValue());
                }
            }

            syncTxxxFrames(id3v2, replacements);
        }
    }

    @Override
    protected void finish(File file, MusicMetadata meta) throws IOException {
        byte[] wavData = Files.readAllBytes(file.toPath());

        String title  = meta.getImmutableSongs().stream().findFirst().map(Song::getTitle).orElse("");
        String year   = meta.getImmutableSongs().stream().findFirst().map(Song::getYear).orElse("");
        String album  = meta.getImmutableAlbums().stream().findFirst().map(Album::getAlbumName).orElse("");
        String artist = meta.getImmutableArtists().stream().map(Artist::getArtistName)
                .reduce((a, b) -> a + ", " + b).orElse("");
        String genre  = meta.getImmutableStyles().stream().findFirst().map(Style::getStyleName).orElse("");

        byte[] info = buildInfo(title, artist, album, year, genre);
        byte[] list = concat("LIST".getBytes(), le32(info.length + 4), "INFO".getBytes(), info);
        byte[] result;
        int dataEnd = findDataEnd(wavData);
        if (dataEnd < 0) result = concat(wavData, list);
        else result = concat(Arrays.copyOf(wavData, dataEnd), list, Arrays.copyOfRange(wavData, dataEnd, wavData.length));
        // 修正 RIFF 头部总大小（包含新增的 INFO chunk 和后续的 ID3 chunk）
        byte[] riffSize = le32(result.length - 8);
        System.arraycopy(riffSize, 0, result, 4, 4);
        Files.write(file.toPath(), result, StandardOpenOption.TRUNCATE_EXISTING);
        log.debug("WAV INFO chunk 写入完成: {}", file.getName());
    }

    private byte[] buildInfo(String title, String artist, String album, String year, String genre) {
        return concat(infoField("INAM", title), infoField("IART", artist),
                infoField("IPRD", album), infoField("ICRD", year.matches("\\d+") ? year : ""),
                infoField("IGNR", genre));
    }

    private byte[] infoField(String id, String value) {
        if (value == null || value.isEmpty()) return new byte[0];
        byte[] val = concat(value.getBytes(GBK), new byte[]{0});
        if (val.length % 2 != 0) val = concat(val, new byte[]{0});
        return concat(id.getBytes(), le32(val.length), val);
    }

    private int findDataEnd(byte[] d) {
        for (int i = 0; i < d.length - 8; i++)
            if (d[i]=='d' && d[i+1]=='a' && d[i+2]=='t' && d[i+3]=='a')
                return i + 8 + le32(d, i + 4);
        return -1;
    }

    private static byte[] le32(int v) { return new byte[]{(byte)v,(byte)(v>>8),(byte)(v>>16),(byte)(v>>24)}; }
    private static int le32(byte[] d, int o) { return (d[o]&255)|((d[o+1]&255)<<8)|((d[o+2]&255)<<16)|((d[o+3]&255)<<24); }
    private static byte[] concat(byte[]... arrs) {
        int len = 0; for (byte[] a : arrs) len += a.length;
        byte[] r = new byte[len]; int p = 0;
        for (byte[] a : arrs) { System.arraycopy(a, 0, r, p, a.length); p += a.length; }
        return r;
    }
}
