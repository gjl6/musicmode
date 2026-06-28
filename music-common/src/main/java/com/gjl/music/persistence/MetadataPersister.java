package com.gjl.music.persistence;

import com.gjl.music.model.MusicMetadata;

import java.util.List;
import java.util.Map;

/**
 * 元数据持久化契约 —— 批量写入音乐元数据到数据库。
 *
 * <p>与 {@code Module} 不同，此接口不含任何 Pipeline 概念（无缓存/缓冲/流式语义），
 * 仅定义纯粹的批量持久化能力，供 common 层及所有子模块使用。</p>
 */
public interface MetadataPersister {

    /**
     * 批量持久化元数据到数据库。
     * Song 走 MERGE，Artist/Album 走 INSERT-only（已存在不覆盖字段）。
     * 用于 Song 管道场景。
     *
     * @param batch 文件路径 → 元数据 的条目列表
     */
    void persistBatch(List<Map.Entry<String, MusicMetadata>> batch);

    /**
     * 批量持久化元数据到数据库 —— 全 MERGE 模式。
     * Song、Artist、Album 全部使用 MERGE/UPSERT。
     * 用于编辑抽屉保存、Artist/Album 管道。
     *
     * @param batch 文件路径 → 元数据 的条目列表
     */
    void persistBatchMergeArtistAlbum(List<Map.Entry<String, MusicMetadata>> batch);
}
