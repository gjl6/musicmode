package com.gjl.music.module.song.split;

import com.gjl.music.exception.ModuleException;
import com.gjl.music.exception.PipelineException;
import com.gjl.music.editor.mapper.SongManageMapper;
import com.gjl.music.model.*;
import com.gjl.music.module.FailurePolicy;
import com.gjl.music.module.song.support.MetadataGapFillingModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 从文件名中用正则提取元数据字段，继承 MetadataGapFillingModule 走"缺则补 → 处理 → 写回"流程。
 *
 * <p>配置格式（通过 configure 传入）：
 * <pre>{@code
 * {
 *   fillMode: "overwrite" | "gapFill",   // 全覆盖 / 缺则补（默认 gapFill）
 *   rules: [
 *     { name: "序号+标题", pattern: "^(\\d+)\\s+(.+)$",
 *       groups: {"1": "tracknumber", "2": "title"}, enabled: true }
 *   ]
 * }
 * }</pre>
 */
@Slf4j
@Component
public class SplitModuleImpl extends MetadataGapFillingModule implements SplitModule {

    public enum FillMode { OVERWRITE, GAP_FILL }

    private List<SplitRule> rules = List.of();
    private FillMode fillMode = FillMode.GAP_FILL;  // 默认安全策略：缺则补

    public SplitModuleImpl(SongManageMapper songManageMapper) {
        super(songManageMapper);
    }

    @Override
    public String name() { return "split-metadata"; }

    @Override
    public FailurePolicy failurePolicy() { return FailurePolicy.SKIP; }

    @Override public String label() { return "元数据拆分"; }

    @Override
    public List<Map<String, Object>> configSchema() {
        return List.of(
            Map.of("key", "fillMode", "label", "填充模式", "type", "select",
                "default", "gapFill",
                "options", List.of(
                    Map.of("value", "gapFill", "label", "缺则补（仅填充空白字段）"),
                    Map.of("value", "overwrite", "label", "全覆盖（覆盖已有字段）")
                )),
            Map.of("key", "rules", "label", "拆分规则", "type", "array",
                "default", List.of(),
                "placeholder", "正则提取规则列表，每条含 name/pattern/groups")
        );
    }

    // ── 配置 ──

    @Override
    @SuppressWarnings("unchecked")
    public void configure(Map<String, Object> options) {
        if (options == null) { this.rules = List.of(); return; }
        Object raw = options.get("rules");
        if (!(raw instanceof List<?> list)) { this.rules = List.of(); return; }
        List<SplitRule> parsed = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> m) {
                SplitRule r = new SplitRule();
                r.name = str(m.get("name"));
                r.pattern = str(m.get("pattern"));
                r.enabled = !Boolean.FALSE.equals(m.get("enabled"));
                Object g = m.get("groups");
                if (g instanceof Map<?, ?> gm) {
                    Map<Integer, String> groups = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> e : gm.entrySet()) {
                        try {
                            int idx = Integer.parseInt(String.valueOf(e.getKey()));
                            String field = String.valueOf(e.getValue());
                            groups.put(idx, field);
                        } catch (NumberFormatException ignored) {
                            log.warn("SplitModule: 规则 group key '{}' 不是有效整数，已跳过", e.getKey());
                        }
                    }
                    r.groups = groups;
                }
                if (r.enabled && r.pattern != null && !r.pattern.isBlank() && !r.groups.isEmpty()) {
                    try {
                        Pattern.compile(r.pattern);
                    } catch (PatternSyntaxException e) {
                        throw new PipelineException(
                                "SplitModule 无效正则 [" + r.name + "]: " + r.pattern, e);
                    }
                    parsed.add(r);
                }
            }
        }
        this.rules = parsed;
        // 读取填充模式：overwrite=全覆盖, gapFill=缺则补（默认）
        Object fm = options.get("fillMode");
        if ("overwrite".equalsIgnoreCase(str(fm))) {
            this.fillMode = FillMode.OVERWRITE;
        } else {
            this.fillMode = FillMode.GAP_FILL;
        }
        log.info("SplitModule 加载 {} 条规则, fillMode={}", parsed.size(), fillMode);
    }

    // ── GapFillingModule 模板方法 ──

    @Override
    protected MusicMetadata processItem(MusicMetadata meta) {
        if (meta == null) {
            throw new ModuleException(name(), "input metadata is null");
        }
        if (rules.isEmpty()) {
            log.warn("SplitModule: 没有配置规则，跳过处理");
            return meta;
        }
        MusicMetadata result = applyRules(meta);
        if (result != meta && !result.getImmutableSongs().isEmpty()) {
            log.debug("SplitModule: 应用规则成功，标题: {}",
                    result.getImmutableSongs().get(0).getTitle());
        }
        return result;
    }

    // ── 核心逻辑 ──

    private MusicMetadata applyRules(MusicMetadata meta) {
        if (rules.isEmpty() || meta.getImmutableSongs().isEmpty()) return meta;

        Song firstSong = meta.getImmutableSongs().get(0);
        String fileName = firstSong.getFileName();
        if (fileName == null || fileName.isBlank()) return meta;

        // 去掉扩展名
        String baseName = fileName;
        int dot = baseName.lastIndexOf('.');
        if (dot > 0) baseName = baseName.substring(0, dot);

        MusicMetadata result = new MusicMetadata();

        // 收集所有启用规则的提取结果，第一条匹配优先
        Map<String, String> extracted = new LinkedHashMap<>();
        for (SplitRule rule : rules) {
            if (!rule.enabled) continue;
            try {
                Pattern p = Pattern.compile(rule.pattern);
                Matcher m = p.matcher(baseName);
                if (m.find()) {
                    for (Map.Entry<Integer, String> g : rule.groups.entrySet()) {
                        if (g.getKey() <= m.groupCount()) {
                            String val = m.group(g.getKey());
                            if (val != null && !val.isBlank()) {
                                extracted.putIfAbsent(g.getValue(), val.trim());
                            }
                        }
                    }
                }
            } catch (RuntimeException e) {
                log.error("SplitModule 规则 [{}] 执行失败: {}", rule.name, e.getMessage());
            }
        }

        if (extracted.isEmpty()) return meta;

        // 填充 Song
        for (Song s : meta.getImmutableSongs()) {
            Song.SongBuilder<?, ?> b = s.toBuilder();
            fillStr(s.getTitle(), extracted.get("title"), b::title);
            fillStr(s.getYear(), extracted.get("year"), b::year);
            fillInt(s.getTrackNumber(), extracted.get("tracknumber"), b::trackNumber);
            fillInt(s.getDiscNumber(), extracted.get("discnumber"), b::discNumber);
            fillStr(s.getLanguage(), extracted.get("language"), b::language);
            fillStr(s.getComposer(), extracted.get("composer"), b::composer);
            fillStr(s.getLyricist(), extracted.get("lyricist"), b::lyricist);
            result.addSong(b.build());
        }

        // 填充 Album
        for (Album a : meta.getImmutableAlbums()) {
            Album.AlbumBuilder<?, ?> b = a.toBuilder();
            fillStr(a.getAlbumName(), extracted.get("album"), b::albumName);
            fillInt(a.getAlbumYear(), extracted.get("albumyear"), b::albumYear);
            result.addAlbum(b.build());
        }

        // 填充 Artist
        for (Artist a : meta.getImmutableArtists()) {
            Artist.ArtistBuilder<?, ?> b = a.toBuilder();
            fillStr(a.getArtistName(), extracted.get("artist"), b::artistName);
            fillStr(a.getCountry(), extracted.get("country"), b::country);
            result.addArtist(b.build());
        }

        // 填充 Style
        for (Style s : meta.getImmutableStyles()) {
            Style.StyleBuilder<?, ?> b = s.toBuilder();
            fillStr(s.getStyleName(), extracted.get("genre"), b::styleName);
            result.addStyle(b.build());
        }

        // 保留未修改的 Lyrics
        for (Lyric l : meta.getImmutableLyrics()) {
            result.addLyric(l);
        }

        return result;
    }

    /** 按 fillMode 填充字符串字段 */
    private void fillStr(String currentVal, String newVal,
                          java.util.function.Consumer<String> setter) {
        if (newVal == null || newVal.isBlank()) return;
        if (fillMode == FillMode.GAP_FILL
                && currentVal != null && !currentVal.isBlank()) return;
        setter.accept(newVal);
    }

    /** 按 fillMode 填充整数字段 */
    private void fillInt(Integer currentVal, String newVal,
                          java.util.function.IntConsumer setter) {
        if (newVal == null || newVal.isBlank()) return;
        if (fillMode == FillMode.GAP_FILL && currentVal != null) return;
        try {
            setter.accept(Integer.parseInt(newVal.trim()));
        } catch (NumberFormatException ignored) {
            log.warn("SplitModule: 无法将 '{}' 解析为整数，字段已跳过", newVal);
        }
    }

    // ── 内部类 ──

    private static class SplitRule {
        String name;
        String pattern;
        Map<Integer, String> groups = Map.of();
        boolean enabled = true;
    }

    private static String str(Object o) {
        return o != null ? o.toString() : null;
    }
}
