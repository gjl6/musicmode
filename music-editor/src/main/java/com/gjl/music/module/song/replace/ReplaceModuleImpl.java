package com.gjl.music.module.song.replace;

import com.gjl.music.exception.ModuleException;
import com.gjl.music.exception.PipelineException;
import com.gjl.music.editor.mapper.SongManageMapper;
import com.gjl.music.model.*;
import com.gjl.music.module.FailurePolicy;
import com.gjl.music.module.song.support.MetadataGapFillingModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 对元数据字段执行文本/正则替换，继承 MetadataGapFillingModule 走"缺则补 → 处理 → 写回"流程。
 *
 * <p>规则格式（通过 configure 传入）：
 * <pre>{@code
 * {
 *   fields: ["song.title", "artist.artistName", ...],   // 目标字段，默认全部
 *   rules: [
 *     { name: "清理来源标签", find: "\\[.*?\\]", replace: "", isRegex: true, enabled: true }
 *   ]
 * }
 * }</pre>
 */
@Slf4j
@Component
public class ReplaceModuleImpl extends MetadataGapFillingModule implements ReplaceModule {

    private static final Set<String> ALL_FIELDS = Set.of(
            "song.title", "song.year", "song.language", "song.composer", "song.lyricist",
            "album.albumName", "album.introduction", "album.company", "album.language",
            "artist.artistName", "artist.introduction", "artist.country",
            "style.styleName", "style.description",
            "lyric.content"
    );

    private Set<String> targetFields = ALL_FIELDS;
    private List<ReplaceRule> rules = List.of();

    public ReplaceModuleImpl(SongManageMapper songManageMapper) {
        super(songManageMapper);
    }

    @Override
    public String name() { return "replace-text"; }

    @Override
    public FailurePolicy failurePolicy() { return FailurePolicy.SKIP; }

    @Override public String label() { return "文本替换"; }

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
                    Map.of("value", "album.introduction", "label", "专辑介绍"),
                    Map.of("value", "album.company", "label", "发行公司"),
                    Map.of("value", "album.language", "label", "专辑语言"),
                    Map.of("value", "artist.artistName", "label", "艺术家名称"),
                    Map.of("value", "artist.introduction", "label", "艺术家介绍"),
                    Map.of("value", "artist.country", "label", "艺术家国家"),
                    Map.of("value", "style.styleName", "label", "流派名称"),
                    Map.of("value", "style.description", "label", "流派描述"),
                    Map.of("value", "lyric.content", "label", "歌词内容")
                )),
            Map.of("key", "rules", "label", "替换规则", "type", "array",
                "default", List.of(),
                "placeholder", "替换规则列表，每条含 name/find/replace/isRegex/enabled")
        );
    }

    // ── 配置 ──

    @Override
    @SuppressWarnings("unchecked")
    public void configure(Map<String, Object> options) {
        if (options == null) { this.rules = List.of(); return; }

        // 目标字段
        Object f = options.get("fields");
        if (f instanceof List<?> fl && !fl.isEmpty()) {
            this.targetFields = new LinkedHashSet<>();
            for (Object item : fl) {
                if (item != null) this.targetFields.add(item.toString());
            }
        } else {
            this.targetFields = ALL_FIELDS;
        }

        // 替换规则
        Object r = options.get("rules");
        if (!(r instanceof List<?> rl)) { this.rules = List.of(); return; }
        List<ReplaceRule> parsed = new ArrayList<>();
        for (Object item : rl) {
            if (item instanceof Map<?, ?> m) {
                ReplaceRule rule = new ReplaceRule();
                rule.name = str(m.get("name"));
                rule.find = str(m.get("find"));
                rule.replace = str(m.get("replace"));
                if (rule.replace == null) rule.replace = "";
                rule.isRegex = Boolean.TRUE.equals(m.get("isRegex"));
                rule.enabled = !Boolean.FALSE.equals(m.get("enabled"));
                if (rule.find != null && !rule.find.isEmpty()) {
                    if (rule.isRegex) {
                        try {
                            Pattern.compile(rule.find);
                        } catch (PatternSyntaxException e) {
                            throw new PipelineException(
                                    "ReplaceModule 无效正则 [" + rule.name + "]: " + rule.find, e);
                        }
                    }
                    parsed.add(rule);
                }
            }
        }
        this.rules = parsed;
        log.info("ReplaceModule 加载 {} 条规则，目标字段: {}", parsed.size(), targetFields);
    }

    // ── GapFillingModule 模板方法 ──

    @Override
    protected MusicMetadata processItem(MusicMetadata meta) {
        if (meta == null) {
            throw new ModuleException(name(), "input metadata is null");
        }
        return applyRules(meta);
    }

    // ── 核心逻辑 ──

    private MusicMetadata applyRules(MusicMetadata meta) {
        if (rules.isEmpty()) return meta;

        MusicMetadata result = new MusicMetadata();

        for (Song s : meta.getImmutableSongs()) {
            Song.SongBuilder<?, ?> b = s.toBuilder();
            if (has("song.title"))       b.title(apply(s.getTitle()));
            if (has("song.year"))        b.year(apply(s.getYear()));
            if (has("song.language"))    b.language(apply(s.getLanguage()));
            if (has("song.composer"))    b.composer(apply(s.getComposer()));
            if (has("song.lyricist"))    b.lyricist(apply(s.getLyricist()));
            result.addSong(b.build());
        }

        for (Album a : meta.getImmutableAlbums()) {
            Album.AlbumBuilder<?, ?> b = a.toBuilder();
            if (has("album.albumName"))     b.albumName(apply(a.getAlbumName()));
            if (has("album.introduction"))  b.introduction(apply(a.getIntroduction()));
            if (has("album.company"))       b.company(apply(a.getCompany()));
            if (has("album.language"))      b.language(apply(a.getLanguage()));
            result.addAlbum(b.build());
        }

        for (Artist a : meta.getImmutableArtists()) {
            Artist.ArtistBuilder<?, ?> b = a.toBuilder();
            if (has("artist.artistName"))    b.artistName(apply(a.getArtistName()));
            if (has("artist.introduction"))  b.introduction(apply(a.getIntroduction()));
            if (has("artist.country"))       b.country(apply(a.getCountry()));
            result.addArtist(b.build());
        }

        for (Style s : meta.getImmutableStyles()) {
            Style.StyleBuilder<?, ?> b = s.toBuilder();
            if (has("style.styleName"))     b.styleName(apply(s.getStyleName()));
            if (has("style.description"))   b.description(apply(s.getDescription()));
            result.addStyle(b.build());
        }

        for (Lyric l : meta.getImmutableLyrics()) {
            Lyric.LyricBuilder<?, ?> b = l.toBuilder();
            if (has("lyric.content"))       b.content(apply(l.getContent()));
            result.addLyric(b.build());
        }

        return result;
    }

    private boolean has(String field) {
        return targetFields.contains("all") || targetFields.contains(field);
    }

    private String apply(String text) {
        if (text == null || text.isEmpty()) return text;
        String result = text;
        for (ReplaceRule rule : rules) {
            if (!rule.enabled) continue;
            if (rule.isRegex) {
                result = applyRegex(result, rule.find, rule.replace);
            } else {
                result = result.replace(rule.find, rule.replace);
            }
        }
        return result;
    }

    /**
     * 用正则替换，如果正则中的 { } 被当作字面量使用（未转义），
     * Java 会抛出 PatternSyntaxException("Illegal repetition")，
     * 此时自动转义 { } 后重试。
     */
    private String applyRegex(String text, String find, String replacement) {
        try {
            return text.replaceAll(find, replacement);
        } catch (PatternSyntaxException e) {
            String escaped = find
                    .replaceAll("(?<!\\\\)\\{", "\\\\{")
                    .replaceAll("(?<!\\\\)\\}", "\\\\}");
            return text.replaceAll(escaped, replacement);
        }
    }

    // ── 内部类 ──

    private static class ReplaceRule {
        String name;
        String find;
        String replace = "";
        boolean isRegex;
        boolean enabled = true;
    }

    private static String str(Object o) {
        return o != null ? o.toString() : null;
    }
}
