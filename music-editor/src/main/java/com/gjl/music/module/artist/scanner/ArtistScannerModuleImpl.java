package com.gjl.music.module.artist.scanner;

import com.gjl.music.editor.mapper.ArtistManageMapper;
import com.gjl.music.module.FailurePolicy;
import com.gjl.music.infra.pipeline.NodeContext;
import com.gjl.music.infra.pipeline.NodeHandler;
import com.gjl.music.infra.pipeline.NodeResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 艺术家扫描模块实现 —— 根据选择规则从 DB 查询艺术家 ID 列表。
 *
 * <p>输入 (NodeContext slot): {@code artist.selection} — ArtistSelection 或 Map
 * <br>输出: {@code node.artist-scanner.output} — List&lt;Long&gt; artist IDs
 *
 * <p>叠加过滤逻辑（style/country/minSongs/maxSongs）在所有 mode 下统一生效，
 * 由 SQL 层 WHERE 子句实现。
 */
@Slf4j
@Component
public class ArtistScannerModuleImpl implements ArtistScannerModule, NodeHandler {

    private final ArtistManageMapper musicMapper;

    public ArtistScannerModuleImpl(ArtistManageMapper musicMapper) {
        this.musicMapper = musicMapper;
    }

    @Override
    public String name() { return "artist-scanner"; }

    @Override
    public FailurePolicy failurePolicy() { return FailurePolicy.FAIL_FAST; }

    @Override
    public boolean isUserVisible() { return false; }

    @Override
    public NodeResult execute(NodeContext ctx) throws Exception {
        // 解析选择规则（兼容 ArtistSelection POJO 和 Map 两种传参方式）
        ArtistSelection sel = resolveSelection(ctx.getSlot("artist.selection"));
        if (sel == null || !sel.isValid()) {
            log.warn("artist-scanner: 未指定有效的 artist.selection，默认使用 mode=all");
            sel = new ArtistSelection();
            sel.setMode("all");
        }

        // 构建叠加过滤参数（letter/keyword 作为正交过滤，与 mode 叠加）
        String letter = sel.getLetter();
        String keyword = sel.getKeyword();
        Integer minSongs = sel.getMinSongs();
        Integer maxSongs = sel.getMaxSongs();
        String style = sel.getStyle();
        String country = sel.getCountry();

        // 根据 mode 查询
        List<Long> artistIds = switch (sel.getMode()) {
            case "all"        -> musicMapper.findAllArtistIds(letter, keyword, minSongs, maxSongs, style, country);
            case "letter"     -> musicMapper.findArtistIdsByLetter(
                                    letter, keyword, minSongs, maxSongs, style, country);
            case "keyword"    -> musicMapper.findArtistIdsByKeyword(
                                    keyword, letter, minSongs, maxSongs, style, country);
            case "incomplete" -> musicMapper.findIncompleteArtistIds(letter, keyword, minSongs, maxSongs, style, country);
            case "unenriched" -> musicMapper.findUnenrichedArtistIds(letter, keyword, minSongs, maxSongs, style, country);
            case "nonstandard"-> musicMapper.findNonstandardArtistIds(letter, keyword, minSongs, maxSongs, style, country);
            case "naked"      -> musicMapper.findNakedArtistIds(letter, keyword, minSongs, maxSongs, style, country);
            case "duplicates" -> musicMapper.findDuplicateArtistIds(letter, keyword, minSongs, maxSongs, style, country);
            case "ids"        -> {
                List<Long> ids = sel.getIds();
                yield (ids != null) ? ids : List.of();
            }
            default -> {
                log.warn("artist-scanner: 未知 mode '{}'，回退 all", sel.getMode());
                yield musicMapper.findAllArtistIds(letter, keyword, minSongs, maxSongs, style, country);
            }
        };

        log.info("artist-scanner: mode={}, 选中 {} 个艺术家", sel.getMode(), artistIds.size());
        ctx.setSlot("node.artist-scanner.output", artistIds);
        NodeResult result = new NodeResult();
        result.addOutput("node.artist-scanner.output", artistIds);
        return result;
    }

    /**
     * 从 Slot 解析 ArtistSelection — 兼容 POJO 和 Map。
     */
    @SuppressWarnings("unchecked")
    static ArtistSelection resolveSelection(Object raw) {
        if (raw instanceof ArtistSelection sel) return sel;
        if (raw instanceof Map<?, ?> map) {
            ArtistSelection sel = new ArtistSelection();
            sel.setMode(stringOrNull(map.get("mode")));
            sel.setLetter(stringOrNull(map.get("letter")));
            sel.setKeyword(stringOrNull(map.get("keyword")));
            sel.setStyle(stringOrNull(map.get("style")));
            sel.setCountry(stringOrNull(map.get("country")));
            Object idsRaw = map.get("ids");
            if (idsRaw instanceof List<?> list) {
                List<Long> ids = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof Number n) ids.add(n.longValue());
                    else if (item instanceof String s) {
                        try { ids.add(Long.parseLong(s)); } catch (NumberFormatException ignored) {}
                    }
                }
                sel.setIds(ids);
            }
            Object minRaw = map.get("minSongs");
            if (minRaw instanceof Number n) sel.setMinSongs(n.intValue());
            Object maxRaw = map.get("maxSongs");
            if (maxRaw instanceof Number n) sel.setMaxSongs(n.intValue());
            return sel;
        }
        return null;
    }

    private static String stringOrNull(Object v) {
        if (v == null) return null;
        String s = v.toString().trim();
        return s.isEmpty() ? null : s;
    }
}
