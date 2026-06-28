package com.gjl.music.module.song.encoding;

import com.gjl.music.exception.ModuleException;
import com.gjl.music.editor.mapper.SongManageMapper;
import com.gjl.music.model.*;
import com.gjl.music.module.FailurePolicy;
import com.gjl.music.module.song.support.MetadataGapFillingModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class EncodingRepairModuleImpl extends MetadataGapFillingModule
        implements EncodingRepairModule {

    private static final Set<String> ALL_REPAIRABLE = Set.of(
            "song.title", "song.year", "song.language", "song.composer", "song.lyricist",
            "album.albumName", "album.albumYear", "album.introduction", "album.company", "album.language",
            "artist.artistName", "artist.country",
            "style.styleName",
            "lyric.content"
    );

    private volatile Set<String> requestedFields;

    public EncodingRepairModuleImpl(SongManageMapper songManageMapper) {
        super(songManageMapper);
    }

    @Override public String name() { return "encoding-repair"; }
    @Override public FailurePolicy failurePolicy() { return FailurePolicy.SKIP; }
    @Override public String label() { return "乱码修复"; }

    @Override
    public List<Map<String, Object>> configSchema() {
        return List.of(
            Map.of("key", "fields", "label", "目标字段", "type", "multiselect",
                "default", List.of("all"),
                "options", List.of(
                    Map.of("value", "all", "label", "全部字段"),
                    Map.of("value", "song.title", "label", "歌曲标题"),
                    Map.of("value", "song.year", "label", "歌曲年份"),
                    Map.of("value", "song.language", "label", "歌曲语言"),
                    Map.of("value", "song.composer", "label", "作曲者"),
                    Map.of("value", "song.lyricist", "label", "作词者"),
                    Map.of("value", "album.albumName", "label", "专辑名称"),
                    Map.of("value", "album.albumYear", "label", "专辑年份"),
                    Map.of("value", "album.introduction", "label", "专辑介绍"),
                    Map.of("value", "album.company", "label", "发行公司"),
                    Map.of("value", "album.language", "label", "专辑语言"),
                    Map.of("value", "artist.artistName", "label", "艺术家名称"),
                    Map.of("value", "artist.country", "label", "艺术家国家"),
                    Map.of("value", "style.styleName", "label", "流派名称"),
                    Map.of("value", "lyric.content", "label", "歌词内容")
                ))
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public void configure(Map<String, Object> options) {
        Object raw = options != null ? options.get("fields") : null;
        if (raw == null) {
            this.requestedFields = ALL_REPAIRABLE;
        } else if (raw instanceof List) {
            List<String> list = (List<String>) raw;
            this.requestedFields = (list.isEmpty() || list.contains("all"))
                    ? ALL_REPAIRABLE : new LinkedHashSet<>(list);
        } else {
            this.requestedFields = "all".equals(raw) ? ALL_REPAIRABLE : ALL_REPAIRABLE;
        }
    }

    // ── 领域方法 ──

    @Override
    public String repair(String text) {
        return MultiEncodingFix.processInput(text);
    }

    @Override
    public MusicMetadata repairMetadata(MusicMetadata meta, Set<String> fields) {
        return repairOnly(meta, fields);
    }

    // ── 流式模式 ──

    @SuppressWarnings("unchecked")
    public Object process(Object input) {
        Map.Entry<Object, MusicMetadata> entry = (Map.Entry<Object, MusicMetadata>) input;
        return Map.entry(entry.getKey(), repairOnly(entry.getValue(), fields()));
    }

    // ── GapFillingModule 模板方法 ──

    @Override
    protected MusicMetadata processItem(MusicMetadata meta) {
        if (meta == null) {
            throw new ModuleException(name(), "input metadata is null");
        }
        return repairOnly(meta, fields());
    }

    private Set<String> fields() {
        return requestedFields != null ? requestedFields : ALL_REPAIRABLE;
    }

    // ── 修复逻辑 ──

    MusicMetadata repairAll(MusicMetadata meta) {
        return repairOnly(meta, ALL_REPAIRABLE);
    }

    MusicMetadata repairOnly(MusicMetadata meta, Set<String> fields) {
        MusicMetadata result = new MusicMetadata();

        for (Song s : meta.getImmutableSongs()) {
            Song.SongBuilder b = s.toBuilder();
            if (fields.contains("song.title"))      b.title(fix(s.getTitle()));
            if (fields.contains("song.year"))        b.year(fix(s.getYear()));
            if (fields.contains("song.language"))    b.language(fix(s.getLanguage()));
            if (fields.contains("song.composer"))    b.composer(fix(s.getComposer()));
            if (fields.contains("song.lyricist"))    b.lyricist(fix(s.getLyricist()));
            result.addSong(b.build());
        }

        for (Album a : meta.getImmutableAlbums()) {
            Album.AlbumBuilder b = a.toBuilder();
            if (fields.contains("album.albumName"))     b.albumName(fix(a.getAlbumName()));
            if (fields.contains("album.albumYear"))      b.albumYear(toString(fix(rawYear(a))));
            if (fields.contains("album.introduction"))   b.introduction(fix(a.getIntroduction()));
            if (fields.contains("album.company"))        b.company(fix(a.getCompany()));
            if (fields.contains("album.language"))       b.language(fix(a.getLanguage()));
            result.addAlbum(b.build());
        }

        for (Artist a : meta.getImmutableArtists()) {
            Artist.ArtistBuilder b = a.toBuilder();
            if (fields.contains("artist.artistName"))   b.artistName(fix(a.getArtistName()));
            if (fields.contains("artist.country"))       b.country(fix(a.getCountry()));
            result.addArtist(b.build());
        }

        for (Style s : meta.getImmutableStyles()) {
            Style.StyleBuilder b = s.toBuilder();
            if (fields.contains("style.styleName"))     b.styleName(fix(s.getStyleName()));
            result.addStyle(b.build());
        }

        for (Lyric l : meta.getImmutableLyrics()) {
            Lyric.LyricBuilder b = l.toBuilder();
            if (fields.contains("lyric.content"))       b.content(fix(l.getContent()));
            result.addLyric(b.build());
        }

        return result;
    }

    // ── 辅助 ──

    private String fix(String text) {
        if (text == null || text.isEmpty()) return text;
        if (!MultiEncodingFix.isGarbled(text)) return text;
        String result = MultiEncodingFix.processInput(text);
        if (result.equals(text)) {
            throw new ModuleException(name(), "编码修复失败: 无法修复乱码文本");
        }
        return result;
    }

    private String rawYear(Album a) {
        Integer y = a.getAlbumYear();
        return y != null ? String.valueOf(y) : null;
    }

    private Integer toString(String s) {
        if (s == null) return null;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) {
            log.debug("EncodingRepair: 无法解析专辑年份 '{}'，已跳过", s);
            return null;
        }
    }
}
