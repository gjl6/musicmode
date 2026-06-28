package com.gjl.music.module.album.albummerge;

import java.util.ArrayList;
import java.util.List;

/**
 * 专辑合并组 —— 一组代表同一个实际专辑的重复记录。
 * <p>
 * sourceIds 会被合并到 targetId，合并后 source 专辑将被删除。
 * affectedSongIds 在执行过程中填充，供标签同步使用。
 *
 * @see com.gjl.music.module.artist.artistmerge.MergeGroup
 */
public class AlbumMergeGroup {

    /** 显示标签，如 "Abbey Road ← abbey road" */
    private String label;

    /** 被合并的源专辑 ID 列表 */
    private List<Long> sourceIds = new ArrayList<>();

    /** 保留的目标专辑 ID */
    private Long targetId;

    /** 合并后受影响的歌曲 ID（执行过程中填充） */
    private List<Long> affectedSongIds = new ArrayList<>();

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public List<Long> getSourceIds() { return sourceIds; }
    public void setSourceIds(List<Long> sourceIds) { this.sourceIds = sourceIds; }

    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }

    public List<Long> getAffectedSongIds() { return affectedSongIds; }
    public void setAffectedSongIds(List<Long> affectedSongIds) { this.affectedSongIds = affectedSongIds; }
}
