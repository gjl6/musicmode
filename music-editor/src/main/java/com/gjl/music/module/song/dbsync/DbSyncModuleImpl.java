package com.gjl.music.module.song.dbsync;

import com.gjl.music.editor.mapper.SongManageMapper;
import com.gjl.music.module.FailurePolicy;
import com.gjl.music.infra.pipeline.NodeContext;
import com.gjl.music.infra.pipeline.NodeHandler;
import com.gjl.music.infra.pipeline.NodeResult;
import com.gjl.music.infra.util.PathUtils;
import com.gjl.music.search.EntityChangeEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * DB 同步模块 —— Scanner/FileSystem 产出文件列表后立即执行，
 * 删除 DB 中磁盘上已不存在的歌曲记录，确保后续模块（如 dedup）查询的是最新状态。
 */
@Slf4j
@Component
public class DbSyncModuleImpl implements DbSyncModule, NodeHandler {

    private final SongManageMapper songManageMapper;
    private final ApplicationEventPublisher eventPublisher;

    public DbSyncModuleImpl(SongManageMapper songManageMapper,
                            ApplicationEventPublisher eventPublisher) {
        this.songManageMapper = songManageMapper;
        this.eventPublisher = eventPublisher;
    }

    @Override public String name() { return "db-sync"; }
    @Override public FailurePolicy failurePolicy() { return FailurePolicy.SKIP; }
    @Override public boolean isUserVisible() { return false; }

    @Override
    public NodeResult execute(NodeContext ctx) throws Exception {
        NodeResult result = new NodeResult();
        // 从传入路径确定 DB 查询范围（末尾带分隔符确保 LIKE 精确匹配）
        List<String> dirs = resolveInputDirs(ctx);
        if (dirs.isEmpty()) {
            log.warn("未设置扫描目录，跳过 DB 同步");
            return result;
        }

        // 按目录范围查询 DB（而非全 root 查询）
        List<Map<String, Object>> dbSongs = songManageMapper.selectSongPathsUnderDirs(dirs);
        if (dbSongs == null || dbSongs.isEmpty()) {
            log.info("DB 同步: 扫描目录下 DB 中无记录，无需清理");
            return result;
        }

        // 构建已扫描文件集（scanner 完成后已写入 SLOT_SCANNED_FILES）
        Set<String> scannedPaths = buildScannedPathSet(ctx);

        if (scannedPaths != null) {
            log.info("DB 同步: 已扫描 {} 个文件，DB 查询到 {} 条记录，内存比对",
                    scannedPaths.size(), dbSongs.size());
        } else {
            log.info("DB 同步: 已扫描文件集不可用，降级为逐文件磁盘检查 ({} 条记录)", dbSongs.size());
        }

        List<Long> toDelete = new ArrayList<>();
        for (Map<String, Object> row : dbSongs) {
            String dbPath = String.valueOf(row.get("FILE_PATH"));
            boolean exists;
            if (scannedPaths != null) {
                exists = scannedPaths.contains(normalizePath(dbPath));
            } else {
                exists = Files.exists(Path.of(dbPath));
            }
            if (!exists) {
                Object idObj = row.get("ID");
                if (idObj instanceof Number n) {
                    toDelete.add(n.longValue());
                }
            }
        }

        if (toDelete.isEmpty()) {
            log.info("DB 同步: 无需清理，DB 与磁盘一致");
            return result;
        }

        log.info("DB 同步: 清理 {} 条已不存在的记录", toDelete.size());
        songManageMapper.deleteSongArtistsBySongIds(toDelete);
        songManageMapper.deleteSongStylesBySongIds(toDelete);
        songManageMapper.deleteLyricsBySongIds(toDelete);
        songManageMapper.deleteSongsByIds(toDelete);

        // ★ 发布索引删除事件
        for (Long songId : toDelete) {
            eventPublisher.publishEvent(EntityChangeEvent.songDeleted(songId));
        }
        log.debug("已发布 {} 个 DB 同步删除事件", toDelete.size());
        return result;
    }

    /** 从 input.paths 提取目录前缀列表（归一化为正斜杠，避免 MySQL LIKE 转义问题） */
    private static List<String> resolveInputDirs(NodeContext context) {
        Object raw = context.getSlot("input.paths");
        if (!(raw instanceof Path[] paths) || paths.length == 0) {
            return List.of();
        }
        Set<String> dirs = new LinkedHashSet<>();
        for (Path p : paths) {
            Path dir = Files.isDirectory(p) ? p : p.getParent();
            if (dir == null) continue;
            String s = PathUtils.normalize(dir.toAbsolutePath().normalize());
            if (!s.endsWith("/")) {
                s += "/";
            }
            dirs.add(s);
        }
        return new ArrayList<>(dirs);
    }

    @SuppressWarnings("unchecked")
    private static Set<String> buildScannedPathSet(NodeContext context) {
        Object raw = context.getSlot("node.scanner.output");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return null;
        }
        Set<String> paths = new HashSet<>();
        for (Object item : list) {
            if (item instanceof Path p) {
                paths.add(p.toAbsolutePath().normalize().toString());
            }
        }
        return paths;
    }

    private static String normalizePath(String raw) {
        return Path.of(raw).toAbsolutePath().normalize().toString();
    }
}
