package com.gjl.music.playback.service;

import java.util.List;
import java.util.Map;

/**
 * 播放计数 / 收藏 / 评分服务。
 *
 * <p>四表设计：play_history（流水）、play_count（预聚合）、user_starred（收藏）、user_rating（评分）。
 * 播放计数采用 Caffeine 30s 本地去重 + MySQL SQL 层时间窗口兜底（集群正确性）。</p>
 */
public interface PlayCountService {

    /** 记录一次播放（事务内：INSERT play_history + UPDATE play_count）。
     *  调用方需预先在事务外查好 song/albumId/artistIds/styleIds */
    void scrobbleTransactional(Long userId, Long songId, Long albumId,
                               List<Long> artistIds, List<Long> styleIds, String source);

    /** 批量获取歌曲播放次数 */
    Map<Long, Integer> getSongPlayCounts(Long userId, List<Long> songIds);

    /** 收藏 */
    void star(Long userId, Long itemId, String itemType);

    /** 取消收藏 */
    void unstar(Long userId, Long itemId, String itemType);

    /** 获取某类型下已收藏的 item ID 列表 */
    List<Long> getStarredIds(Long userId, String itemType);

    /** 评分（1-5） */
    void rate(Long userId, Long itemId, String itemType, int rating);

    /** 批量获取用户评分（song 维度，用于请求级预加载） */
    Map<Long, Integer> getUserRatings(Long userId, List<Long> songIds);

    /** 通用 item 级清理（play_count + user_starred + user_rating） */
    void deleteByItem(Long itemId, String itemType);

    /** 歌曲删除时清理流水 + 聚合/收藏/评分 */
    void deleteBySongId(Long songId);
}
