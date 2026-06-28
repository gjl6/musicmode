package com.gjl.music.module.artist.artistmerge;

import com.gjl.music.mapper.ArtistMapper;
import com.gjl.music.mapper.SongMapper;
import com.gjl.music.editor.mapper.ArtistManageMapper;
import com.gjl.music.model.Artist;
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
 * 艺术家合并模块 —— Pipeline 批量合并重复艺术家。
 *
 * <p>从 NodeContext 读取 mergeGroups，逐组合并：
 * <ol>
 *   <li>song_artist 冲突处理（已有 target 则删源，否则改 target）</li>
 *   <li>album.artist_id 转移</li>
 *   <li>删除 source 艺术家</li>
 *   <li>更新 target 的 album_count / song_count</li>
 *   <li>异步标签同步</li>
 * </ol>
 *
 * <p>FailurePolicy.SKIP — 一组合并失败不影响其余。
 */
@Slf4j
@Component
public class ArtistMergeModuleImpl implements Module, NodeHandler, ArtistMergeModule {

    private final ArtistMapper artistMapper;
    private final ArtistManageMapper artistManageMapper;
    private final SongMapper songMapper;
    private final TagSyncService tagSyncService;
    private final ApplicationEventPublisher eventPublisher;

    public ArtistMergeModuleImpl(ArtistMapper artistMapper,
                                  ArtistManageMapper artistManageMapper,
                                  SongMapper songMapper,
                                  TagSyncService tagSyncService,
                                  ApplicationEventPublisher eventPublisher) {
        this.artistMapper = artistMapper;
        this.artistManageMapper = artistManageMapper;
        this.songMapper = songMapper;
        this.tagSyncService = tagSyncService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public String name() { return "artist-merge"; }

    @Override
    public FailurePolicy failurePolicy() { return FailurePolicy.SKIP; }

    @Override
    public String label() { return "合并艺术家"; }

    @Override
    public ExecutorType executorType() { return ExecutorType.VIRTUAL; }

    @Override
    public NodeResult execute(NodeContext ctx) throws Exception {
        NodeResult result = new NodeResult();

        @SuppressWarnings("unchecked")
        List<MergeGroup> groups = ctx.getSlot("merge.groups");
        if (groups == null || groups.isEmpty()) {
            log.warn("artist-merge: 无合并组，跳过");
            return result;
        }

        int total = groups.size();
        ctx.setSlot("node.artist-merge.total", total);
        log.info("artist-merge: 开始合并 {} 组", total);

        for (MergeGroup group : groups) {
            ctx.checkPause();
            try {
                mergeOneGroup(group);
                ctx.reportItemComplete(group.getLabel(), true, null);
                result.addItemResult(group.getLabel(), true, null);
                log.info("artist-merge: {} ✓", group.getLabel());
            } catch (Exception e) {
                log.error("artist-merge: {} ✗ — {}", group.getLabel(), e.getMessage(), e);
                ctx.reportItemComplete(group.getLabel(), false, e.getMessage());
                result.addItemResult(group.getLabel(), false, e.getMessage());
            }
        }

        result.addOutput("merge.groups", groups);
        return result;
    }

    // ── 单组合并 ──

    private void mergeOneGroup(MergeGroup group) {
        Long targetId = group.getTargetId();
        Artist target = artistMapper.findArtistById(targetId);
        if (target == null) {
            throw new IllegalArgumentException("目标艺术家不存在: " + targetId);
        }

        for (Long sourceId : group.getSourceIds()) {
            if (sourceId.equals(targetId)) continue;

            Artist source = artistMapper.findArtistById(sourceId);
            if (source == null) {
                log.warn("源艺术家不存在: {}, 跳过", sourceId);
                continue;
            }

            // 1. 处理 song_artist 冲突
            artistManageMapper.deleteConflictingSongArtists(sourceId, targetId);
            artistManageMapper.reassignSongArtists(sourceId, targetId);

            // 2. 转移专辑
            artistManageMapper.reassignAlbumArtist(sourceId, targetId);

            // 3. 删除源艺术家
            artistManageMapper.deleteArtist(sourceId);

            // ★ 发布索引事件：源艺术家删除 + 目标艺术家更新
            eventPublisher.publishEvent(EntityChangeEvent.artistDeleted(sourceId));
        }

        // 目标艺术家更新
        eventPublisher.publishEvent(EntityChangeEvent.artistUpdated(targetId));

        // 4. 更新计数
        songMapper.updateArtistAlbumCounts(List.of(targetId));
        songMapper.updateArtistSongCounts(List.of(targetId));

        // 5. 收集受影响歌曲 ID
        List<Long> affectedSongIds = new ArrayList<>();
        List<Song> songs = songMapper.findSongsByArtistId(targetId);
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

        // 6. 异步标签同步
        tagSyncService.syncArtistChange(targetId);
    }
}
