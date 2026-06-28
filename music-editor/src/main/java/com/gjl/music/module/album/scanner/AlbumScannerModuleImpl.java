package com.gjl.music.module.album.scanner;

import com.gjl.music.editor.mapper.AlbumManageMapper;
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
 * 专辑扫描模块实现 —???根据选择规则???DB 查询专辑 ID 列表??? *
 * <p>输入 (NodeContext slot): {@code album.selection} ???AlbumSelection ???Map
 * <br>输出: {@code node.album-scanner.output} ???List&lt;Long&gt; album IDs
 *
 * <p>叠加过滤逻辑（artistId/minSongs/maxSongs）在所???mode 下统一生效??? * ???SQL ???WHERE 子句实现??? *
 * @see com.gjl.music.module.artist.scanner.ArtistScannerModuleImpl
 */
@Slf4j
@Component
public class AlbumScannerModuleImpl implements AlbumScannerModule, NodeHandler {

    private final AlbumManageMapper albumManageMapper;

    public AlbumScannerModuleImpl(AlbumManageMapper albumManageMapper) {
        this.albumManageMapper = albumManageMapper;
    }

    @Override
    public String name() { return "album-scanner"; }

    @Override
    public FailurePolicy failurePolicy() { return FailurePolicy.FAIL_FAST; }

    @Override
    public boolean isUserVisible() { return false; }

    @Override
    public NodeResult execute(NodeContext ctx) throws Exception {
        // 解析选择规则（兼容 AlbumSelection POJO 和 Map 两种传参方式）
        AlbumSelection sel = resolveSelection(ctx.getSlot("album.selection"));
        if (sel == null || !sel.isValid()) {
            log.warn("album-scanner: 未指定有效的 album.selection，默认使???mode=all");
            sel = new AlbumSelection();
            sel.setMode("all");
        }

        // 构建叠加过滤参数
        String letter = sel.getLetter();
        String keyword = sel.getKeyword();
        Long artistId = sel.getArtistId();
        Integer minSongs = sel.getMinSongs();
        Integer maxSongs = sel.getMaxSongs();

        // 根据 mode 查询
        List<Long> albumIds = switch (sel.getMode()) {
            case "all"        -> albumManageMapper.findAllAlbumIds(letter, keyword, artistId, minSongs, maxSongs);
            case "letter"     -> albumManageMapper.findAlbumIdsByLetter(letter, keyword, artistId, minSongs, maxSongs);
            case "keyword"    -> albumManageMapper.findAlbumIdsByKeyword(keyword, letter, artistId, minSongs, maxSongs);
            case "incomplete" -> albumManageMapper.findIncompleteAlbumIds(letter, keyword, artistId, minSongs, maxSongs);
            case "unenriched" -> albumManageMapper.findUnenrichedAlbumIds(letter, keyword, artistId, minSongs, maxSongs);
            case "naked"      -> albumManageMapper.findNakedAlbumIds(letter, keyword, artistId, minSongs, maxSongs);
            case "duplicates" -> albumManageMapper.findDuplicateAlbumIds(letter, keyword, artistId, minSongs, maxSongs);
            case "ids"        -> {
                List<Long> ids = sel.getIds();
                yield (ids != null) ? ids : List.of();
            }
            default -> {
                log.warn("album-scanner: 未知 mode '{}'，回退 all", sel.getMode());
                yield albumManageMapper.findAllAlbumIds(letter, keyword, artistId, minSongs, maxSongs);
            }
        };

        log.info("album-scanner: mode={}, 选中 {} 个专辑", sel.getMode(), albumIds.size());
        ctx.setSlot("node.album-scanner.output", albumIds);
        NodeResult result = new NodeResult();
        result.addOutput("node.album-scanner.output", albumIds);
        return result;
    }

    /**
     * ???Slot 解析 AlbumSelection ???兼容 POJO ???Map???     */
    @SuppressWarnings("unchecked")
    static AlbumSelection resolveSelection(Object raw) {
        if (raw instanceof AlbumSelection sel) return sel;
        if (raw instanceof Map<?, ?> map) {
            AlbumSelection sel = new AlbumSelection();
            sel.setMode(stringOrNull(map.get("mode")));
            sel.setLetter(stringOrNull(map.get("letter")));
            sel.setKeyword(stringOrNull(map.get("keyword")));
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
            Object artistIdRaw = map.get("artistId");
            if (artistIdRaw instanceof Number n) sel.setArtistId(n.longValue());
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
