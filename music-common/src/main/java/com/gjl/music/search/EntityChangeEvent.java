package com.gjl.music.search;

/**
 * 实体变更事件 — 歌曲/专辑/艺术家在 DB 中创建、更新或删除时发布。
 *
 * <p>由数据变更点（MetadataPersister、DeleteModule、Merge 模块等）发布，
 * {@link IndexEventListener} 异步消费，驱动 Lucene 索引实时增删改。
 *
 * @param entityType 实体类型：SONG / ALBUM / ARTIST
 * @param changeType 变更类型：CREATED / UPDATED / DELETED
 * @param entityId   实体 ID
 */
public record EntityChangeEvent(
        EntityType entityType,
        ChangeType changeType,
        Long entityId
) {

    public enum EntityType { SONG, ALBUM, ARTIST }
    public enum ChangeType { CREATED, UPDATED, DELETED }

    /** 快捷工厂：歌曲创建 */
    public static EntityChangeEvent songCreated(Long id) {
        return new EntityChangeEvent(EntityType.SONG, ChangeType.CREATED, id);
    }

    /** 快捷工厂：歌曲更新 */
    public static EntityChangeEvent songUpdated(Long id) {
        return new EntityChangeEvent(EntityType.SONG, ChangeType.UPDATED, id);
    }

    /** 快捷工厂：歌曲删除 */
    public static EntityChangeEvent songDeleted(Long id) {
        return new EntityChangeEvent(EntityType.SONG, ChangeType.DELETED, id);
    }

    /** 快捷工厂：专辑创建 */
    public static EntityChangeEvent albumCreated(Long id) {
        return new EntityChangeEvent(EntityType.ALBUM, ChangeType.CREATED, id);
    }

    /** 快捷工厂：专辑更新 */
    public static EntityChangeEvent albumUpdated(Long id) {
        return new EntityChangeEvent(EntityType.ALBUM, ChangeType.UPDATED, id);
    }

    /** 快捷工厂：专辑删除 */
    public static EntityChangeEvent albumDeleted(Long id) {
        return new EntityChangeEvent(EntityType.ALBUM, ChangeType.DELETED, id);
    }

    /** 快捷工厂：艺术家创建 */
    public static EntityChangeEvent artistCreated(Long id) {
        return new EntityChangeEvent(EntityType.ARTIST, ChangeType.CREATED, id);
    }

    /** 快捷工厂：艺术家更新 */
    public static EntityChangeEvent artistUpdated(Long id) {
        return new EntityChangeEvent(EntityType.ARTIST, ChangeType.UPDATED, id);
    }

    /** 快捷工厂：艺术家删除 */
    public static EntityChangeEvent artistDeleted(Long id) {
        return new EntityChangeEvent(EntityType.ARTIST, ChangeType.DELETED, id);
    }
}
