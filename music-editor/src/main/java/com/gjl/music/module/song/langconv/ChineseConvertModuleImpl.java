package com.gjl.music.module.song.langconv;

import com.github.houbb.opencc4j.util.ZhConverterUtil;
import com.gjl.music.exception.ModuleException;
import com.gjl.music.editor.mapper.SongManageMapper;
import com.gjl.music.model.*;
import com.gjl.music.module.FailurePolicy;
import com.gjl.music.module.song.support.MetadataGapFillingModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
public class ChineseConvertModuleImpl extends MetadataGapFillingModule implements ChineseConvertModule {

    private static final String DIRECTION_TO_TRADITIONAL = "toTraditional";

    private static final Set<String> ALL_FIELDS = Set.of(
        "song.title", "song.composer", "song.lyricist",
        "artist.artistName", "artist.country", "artist.introduction",
        "album.albumName", "album.introduction", "album.company",
        "style.styleName", "style.description",
        "lyric.content"
    );

    private String direction = "toSimplified";
    private Set<String> targetFields = ALL_FIELDS;

    public ChineseConvertModuleImpl(SongManageMapper songManageMapper) {
        super(songManageMapper);
    }

    @Override public String name() { return "chinese-convert"; }
    @Override public FailurePolicy failurePolicy() { return FailurePolicy.SKIP; }
    @Override public String label() { return "繁简转换"; }

    @Override
    public List<Map<String, Object>> configSchema() {
        return List.of(
            Map.of("key", "direction", "label", "转换方向", "type", "select",
                "default", "toSimplified",
                "options", List.of(
                    Map.of("value", "toSimplified", "label", "繁体→简体"),
                    Map.of("value", "toTraditional", "label", "简体→繁体")
                )),
            Map.of("key", "fields", "label", "目标字段", "type", "multiselect",
                "default", List.of("all"),
                "options", List.of(
                    Map.of("value", "all", "label", "全部字段"),
                    Map.of("value", "song.title", "label", "歌曲标题"),
                    Map.of("value", "song.composer", "label", "作曲者"),
                    Map.of("value", "song.lyricist", "label", "作词者"),
                    Map.of("value", "artist.artistName", "label", "艺术家名称"),
                    Map.of("value", "artist.country", "label", "艺术家国家"),
                    Map.of("value", "artist.introduction", "label", "艺术家介绍"),
                    Map.of("value", "album.albumName", "label", "专辑名称"),
                    Map.of("value", "album.introduction", "label", "专辑介绍"),
                    Map.of("value", "album.company", "label", "发行公司"),
                    Map.of("value", "style.styleName", "label", "流派名称"),
                    Map.of("value", "style.description", "label", "流派描述"),
                    Map.of("value", "lyric.content", "label", "歌词内容")
                ))
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public void configure(Map<String, Object> options) {
        if (options == null) return;
        if (options.containsKey("direction")) {
            String v = String.valueOf(options.get("direction"));
            if (DIRECTION_TO_TRADITIONAL.equals(v)) {
                this.direction = DIRECTION_TO_TRADITIONAL;
            } else {
                this.direction = "toSimplified";
            }
        }
        Object f = options.get("fields");
        if (f instanceof List<?> fl && !fl.isEmpty()) {
            this.targetFields = new LinkedHashSet<>();
            for (Object item : fl) {
                if (item != null) this.targetFields.add(item.toString());
            }
        }
    }

    // ── 领域方法 ──

    @Override
    public String toSimplified(String text) {
        return ZhConverterUtil.toSimple(text);
    }

    @Override
    public String toTraditional(String text) {
        return ZhConverterUtil.toTraditional(text);
    }

    @Override
    public MusicMetadata convert(MusicMetadata meta) {
        return convertMetadata(meta);
    }

    // ── GapFillingModule 模板方法 ──

    @Override
    protected MusicMetadata processItem(MusicMetadata meta) {
        if (meta == null) {
            throw new ModuleException(name(), "input metadata is null");
        }
        return convertMetadata(meta);
    }

    // ── 内部转换逻辑 ──

    private boolean has(String field) {
        return targetFields.contains(field);
    }

    private String convertText(String text) {
        if (text == null || text.isEmpty()) return text;
        try {
            return DIRECTION_TO_TRADITIONAL.equals(direction)
                    ? ZhConverterUtil.toTraditional(text)
                    : ZhConverterUtil.toSimple(text);
        } catch (Exception e) {
            throw new ModuleException(name(), "文本转换失败: " + e.getMessage(), e);
        }
    }

    MusicMetadata convertMetadata(MusicMetadata metadata) {
        MusicMetadata result = new MusicMetadata();
        metadata.getImmutableSongs().stream()
                .map(this::convertSong)
                .forEach(result::addSong);
        metadata.getImmutableArtists().stream()
                .map(this::convertArtist)
                .forEach(result::addArtist);
        metadata.getImmutableAlbums().stream()
                .map(this::convertAlbum)
                .forEach(result::addAlbum);
        metadata.getImmutableStyles().stream()
                .map(this::convertStyle)
                .forEach(result::addStyle);
        metadata.getImmutableLyrics().stream()
                .map(this::convertLyric)
                .forEach(result::addLyric);
        return result;
    }

    private Song convertSong(Song s) {
        return s.toBuilder()
                .title(has("song.title") ? convertText(s.getTitle()) : s.getTitle())
                .composer(has("song.composer") ? convertText(s.getComposer()) : s.getComposer())
                .lyricist(has("song.lyricist") ? convertText(s.getLyricist()) : s.getLyricist())
                .build();
    }

    private Artist convertArtist(Artist a) {
        return a.toBuilder()
                .artistName(has("artist.artistName") ? convertText(a.getArtistName()) : a.getArtistName())
                .country(has("artist.country") ? convertText(a.getCountry()) : a.getCountry())
                .introduction(has("artist.introduction") ? convertText(a.getIntroduction()) : a.getIntroduction())
                .build();
    }

    private Album convertAlbum(Album a) {
        return a.toBuilder()
                .albumName(has("album.albumName") ? convertText(a.getAlbumName()) : a.getAlbumName())
                .introduction(has("album.introduction") ? convertText(a.getIntroduction()) : a.getIntroduction())
                .company(has("album.company") ? convertText(a.getCompany()) : a.getCompany())
                .build();
    }

    private Style convertStyle(Style s) {
        return s.toBuilder()
                .styleName(has("style.styleName") ? convertText(s.getStyleName()) : s.getStyleName())
                .description(has("style.description") ? convertText(s.getDescription()) : s.getDescription())
                .build();
    }

    private Lyric convertLyric(Lyric l) {
        return l.toBuilder()
                .content(has("lyric.content") ? convertText(l.getContent()) : l.getContent())
                .build();
    }
}
