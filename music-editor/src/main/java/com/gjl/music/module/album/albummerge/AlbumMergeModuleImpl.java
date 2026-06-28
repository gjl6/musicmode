package com.gjl.music.module.album.albummerge;

import com.gjl.music.mapper.AlbumMapper;
import com.gjl.music.mapper.SongMapper;
import com.gjl.music.editor.mapper.AlbumManageMapper;
import com.gjl.music.model.Album;
import com.gjl.music.model.Song;
import com.gjl.music.module.FailurePolicy;
import com.gjl.music.module.Module;
import com.gjl.music.infra.pipeline.NodeContext;
import com.gjl.music.infra.pipeline.NodeHandler;
import com.gjl.music.infra.pipeline.NodeResult;
import com.gjl.music.search.EntityChangeEvent;
import com.gjl.music.service.song.TagSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 专辑合并模块 — Pipeline 批量合并重复专辑。
 *
 * <p>从 NodeContext 读取 mergeGroups，逐组合并。</p>
 * <ol>
 *   <li>将 source 专辑的歌曲 albumId 转移到 target</li>
 *   <li>song_album 冲突处理（已在 target 则删源，否则转移到 target）</li>
 *   <li>删除 source 专辑</li>
 *   <li>更新 target 的 song_count</li>
 *   <li>异步标签同步</li>
 * </ol>
 *
 * <p>FailurePolicy.SKIP — 单组合并失败不影响其余。</p>
 * @see com.gjl.music.module.artist.artistmerge.ArtistMergeModuleImpl
 */
@Slf4j
@Component
public class AlbumMergeModuleImpl implements Module, NodeHandler, AlbumMergeModule {

    private final AlbumMapper albumMapper;
    private final AlbumManageMapper albumManageMapper;
    private final SongMapper songMapper;
    private final TagSyncService tagSyncService;
    private final ApplicationEventPublisher eventPublisher;

    public AlbumMergeModuleImpl(AlbumMapper albumMapper, AlbumManageMapper albumManageMapper,
                                 SongMapper songMapper, TagSyncService tagSyncService,
                                 ApplicationEventPublisher eventPublisher) {
        this.albumMapper = albumMapper;
        this.albumManageMapper = albumManageMapper;
        this.songMapper = songMapper;
        this.tagSyncService = tagSyncService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public String name() { return "album-merge"; }

    @Override
    public FailurePolicy failurePolicy() { return FailurePolicy.SKIP; }

    @Override
    public String label() { return "合并专辑"; }

    @Override
    public ExecutorType executorType() { return ExecutorType.VIRTUAL; }

    @Override
    public NodeResult execute(NodeContext ctx) throws Exception {
        NodeResult result = new NodeResult();

        @SuppressWarnings("unchecked")
        List<AlbumMergeGroup> groups = ctx.getSlot("merge.groups");
        if (groups == null || groups.isEmpty()) {
            log.warn("album-merge: 无合并组，跳过");
            return result;
        }

        int total = groups.size();
        ctx.setSlot("node.album-merge.total", total);
        log.info("album-merge: 开始合并 {} 组", total);

        for (AlbumMergeGroup group : groups) {
            ctx.checkPause();
            try {
                mergeOneGroup(group);
                ctx.reportItemComplete(group.getLabel(), true, null);
                result.addItemResult(group.getLabel(), true, null);
                log.info("album-merge: {} 完成", group.getLabel());
            } catch (Exception e) {
                log.error("album-merge: {} 合并失败: {}", group.getLabel(), e.getMessage(), e);
                ctx.reportItemComplete(group.getLabel(), false, e.getMessage());
                result.addItemResult(group.getLabel(), false, e.getMessage());
                // SKIP: 继续下一组
            }
        }

        result.addOutput("merge.groups", groups);
        return result;
    }

    // ── 单组合并 ──

    private void mergeOneGroup(AlbumMergeGroup group) {
        Long targetId = group.getTargetId();
        Album target = albumMapper.findAlbumById(targetId);
        if (target == null) {
            throw new IllegalArgumentException("目标专辑不存在: " + targetId);
        }

        for (Long sourceId : group.getSourceIds()) {
            if (sourceId.equals(targetId)) continue;

            Album source = albumMapper.findAlbumById(sourceId);
            if (source == null) {
                log.warn("源专辑不存在: {}, 跳过", sourceId);
                continue;
            }

            // 1. 处理 song_album 冲突：删除 source 中已关联 target 的歌曲
            albumManageMapper.deleteConflictingSongAlbums(sourceId, targetId);
            // 将剩余的歌曲从 source 移到 target
            albumManageMapper.reassignSongAlbum(sourceId, targetId);

            // 2. 删除源专辑
            albumManageMapper.deleteAlbum(sourceId);

            // ★ 发布索引事件：源专辑删除 + 目标专辑更新
            eventPublisher.publishEvent(EntityChangeEvent.albumDeleted(sourceId));
        }

        // 目标专辑更新
        eventPublisher.publishEvent(EntityChangeEvent.albumUpdated(targetId));

        // 3. 更新计数
        songMapper.updateAlbumSongCounts(List.of(targetId));

        // 4. 收集受影响歌曲 ID
        List<Long> affectedSongIds = new ArrayList<>();
        List<Song> songs = songMapper.findSongsByAlbumId(targetId);
        if (songs != null) {
            for (Song s : songs) {
                if (s.getId() != null) {
                    try {
                        affectedSongIds.add(Long.parseLong(s.getId()));
                    } catch (NumberFormatException e) {
                        log.warn("无法解析 songId: {}", s.getId());
                    }
                }
            }
        }
        group.setAffectedSongIds(affectedSongIds);

        // 5. 异步标签同步
        tagSyncService.syncAlbumChange(targetId);
    }
}
