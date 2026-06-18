package com.gjl.music.parser;

import com.gjl.music.infra.util.FileHashUtils;
import com.gjl.music.model.Song;
import lombok.extern.slf4j.Slf4j;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.audio.flac.metadatablock.MetadataBlockDataPicture;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagField;
import org.jaudiotagger.tag.TagTextField;
import org.jaudiotagger.tag.flac.FlacTag;
import org.jaudiotagger.tag.vorbiscomment.VorbisCommentTag;

import java.io.File;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;


@Slf4j
public class FlacParser extends DefaultParser {


    private static final Set<String> ALREADY_HANDLED = Set.of(
            "TITLE", "ARTIST", "ARTISTS", "ALBUMARTIST", "ALBUMARTISTS",
            "ALBUM", "TRACKNUMBER", "DISCNUMBER", "DATE", "GENRE",
            "COMPOSER", "LYRICIST", "PERFORMER", "COMMENT", "LANGUAGE",
            "COUNTRY", "ISRC", "COPYRIGHT", "LABEL", "LYRICS",
            "COMPILATION", "LIVE", "IS_SOUNDTRACK", "BPM", "KEY",
            "METADATA_BLOCK_PICTURE", "COVERART", "COVERARTMIME",
            "ACOUSTID_FINGERPRINT", "ACOUSTID_ID",
            "REPLAYGAIN_TRACK_GAIN", "REPLAYGAIN_TRACK_PEAK",
            "REPLAYGAIN_ALBUM_GAIN", "REPLAYGAIN_ALBUM_PEAK",
            "VENDOR", "TITLESORT", "ARTISTSORT", "ALBUMSORT",
            "TRACKTOTAL", "DISCTOTAL", "MUSICBRAINZ_ARTISTID",
            "MUSICBRAINZ_ALBUMID", "MUSICBRAINZ_TRACKID",
            "MUSICBRAINZ_ALBUMARTISTID", "MUSICBRAINZ_RELEASEGROUPID",
            "ALBUMTYPE", "ALBUMSTATUS", "RELEASECOUNTRY",
            "SCRIPT", "MEDIA"
    );

    @Override
    protected Song extractSong(File file, Tag tag, AudioHeader header, String fileName, String filePath) {
        Song.SongBuilder<?, ?> builder = super.extractSong(file, tag, header, fileName, filePath).toBuilder();

        if (tag instanceof FlacTag flacTag) {
            VorbisCommentTag vc = flacTag.getVorbisCommentTag();
            Map<String, String> extra = new LinkedHashMap<>();

            Iterator<TagField> it = vc.getFields();
            while (it.hasNext()) {
                TagField f = it.next();
                String key = f.getId().toUpperCase();
                if (ALREADY_HANDLED.contains(key)) continue;
                if (f instanceof TagTextField tf) {
                    String val = tf.getContent();
                    if (val != null && !val.isBlank()) extra.putIfAbsent(key, val.trim());
                }
            }

                        String arranger = getVorbisField(vc, "ARRANGER");
            if (arranger != null && builder.build().getArranger() == null) builder.arranger(arranger);
            String producer = getVorbisField(vc, "PRODUCER");
            if (producer != null && builder.build().getProducer() == null) builder.producer(producer);

                        String acoustid = getVorbisField(vc, "ACOUSTID_FINGERPRINT");
            if (acoustid == null) acoustid = getVorbisField(vc, "ACOUSTID_ID");
            if (acoustid != null && builder.build().getFingerprint() == null) builder.fingerprint(acoustid);

            if (!extra.isEmpty()) builder.extraTags(toJson(extra));
        }

        return builder.build();
    }

    @Override
    protected String extractCover(Tag tag, String filePath) {
        String cover = super.extractCover(tag, filePath);
        if (cover != null) return cover;

                if (tag instanceof FlacTag flacTag) {
            List<MetadataBlockDataPicture> pictures = flacTag.getImages();
            if (pictures != null && !pictures.isEmpty()) {
                try {
                    MetadataBlockDataPicture pic = pictures.getFirst();
                    byte[] data = pic.getImageData();
                    if (data == null || data.length == 0) return null;
                    String mime = pic.getMimeType();
                    String ext = (mime != null && mime.contains("png")) ? "png" : "jpg";
                    File out = coverFileFor(filePath, ext);
                    if (!out.exists()) {
                        out.getParentFile().mkdirs();
                        try (FileOutputStream fos = new FileOutputStream(out)) { fos.write(data); }
                    }
                    return java.nio.file.Path.of(coversDir).toAbsolutePath().normalize()
                            .relativize(out.toPath().toAbsolutePath().normalize())
                            .toString().replace('\\', '/');
                } catch (Exception e) {
                    log.warn("FLAC原生封面提取失败: {}", e.getMessage());
                }
            }
        }
        return null;
    }

    private String getVorbisField(VorbisCommentTag vc, String fieldName) {
        try {
            List<TagField> fields = vc.getFields(fieldName);
            if (fields != null && !fields.isEmpty() && fields.getFirst() instanceof TagTextField tf) {
                String val = tf.getContent();
                return (val != null && !val.isBlank()) ? val.trim() : null;
            }
        } catch (Exception e) {  }
        return null;
    }

    private String shortHash(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.substring(0, 8);
        } catch (NoSuchAlgorithmException e) { return "0"; }
    }
}
