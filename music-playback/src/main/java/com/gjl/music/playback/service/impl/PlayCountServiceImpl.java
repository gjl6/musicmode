package com.gjl.music.playback.service.impl;
import com.gjl.music.playback.service.PlayCountService;

import com.gjl.music.playback.mapper.*;
import com.gjl.music.playback.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;


@Slf4j
@Service
public class PlayCountServiceImpl implements PlayCountService {

    private final PlayHistoryMapper playHistoryMapper;
    private final PlayCountMapper playCountMapper;
    private final UserStarredMapper userStarredMapper;
    private final UserRatingMapper userRatingMapper;
    private final boolean isH2;

    public PlayCountServiceImpl(PlayHistoryMapper playHistoryMapper,
                                 PlayCountMapper playCountMapper,
                                 UserStarredMapper userStarredMapper,
                                 UserRatingMapper userRatingMapper,
                                 @Value("${spring.datasource.url:}") String datasourceUrl) {
        this.playHistoryMapper = playHistoryMapper;
        this.playCountMapper = playCountMapper;
        this.userStarredMapper = userStarredMapper;
        this.userRatingMapper = userRatingMapper;
        this.isH2 = datasourceUrl != null && datasourceUrl.startsWith("jdbc:h2");
    }


    private final Cache<String, Boolean> recentPlays =
            Caffeine.newBuilder()
                    .expireAfterWrite(30, TimeUnit.SECONDS)
                    .maximumSize(10000)
                    .build();

    private boolean isDuplicateLocally(Long userId, Long songId) {
        String key = userId + ":" + songId;
        return Boolean.TRUE.equals(recentPlays.asMap().putIfAbsent(key, Boolean.TRUE));
    }


    @Override
    @Transactional
    public void scrobbleTransactional(Long userId, Long songId, Long albumId,
                                       List<Long> artistIds, List<Long> styleIds, String source) {
        if (userId == null || songId == null) return;
                if (isDuplicateLocally(userId, songId)) return;

                playHistoryMapper.insert(PlayHistory.builder()
                .userId(userId).songId(songId)
                .playedAt(LocalDateTime.now()).source(source != null ? source : "web")
                .build());

                inc(userId, songId, "song");
        if (albumId != null) inc(userId, albumId, "album");
        if (artistIds != null) artistIds.forEach(aid -> inc(userId, aid, "artist"));
        if (styleIds != null) styleIds.forEach(sid -> inc(userId, sid, "style"));
    }


    private void inc(Long userId, Long itemId, String itemType) {
        playCountMapper.incPlayCount(userId, itemId, itemType);
    }


    @Override
    public Map<Long, Integer> getSongPlayCounts(Long userId, List<Long> songIds) {
        if (userId == null || songIds == null || songIds.isEmpty()) return Map.of();
        return playCountMapper.batchGetSongCounts(userId, songIds);
    }


    @Override
    public void star(Long userId, Long itemId, String itemType) {
        if (userId == null || itemId == null || itemType == null) {
            log.warn("[PlayCount] star 参数空: userId={}, itemId={}, itemType={}", userId, itemId, itemType);
            return;
        }
        log.info("[PlayCount] star: userId={}, itemId={}, itemType={}", userId, itemId, itemType);
        userStarredMapper.insert(UserStarred.builder()
                .userId(userId).itemId(itemId).itemType(itemType)
                .starredAt(LocalDateTime.now()).build());
    }

    @Override
    public void unstar(Long userId, Long itemId, String itemType) {
        if (userId == null || itemId == null || itemType == null) {
            log.warn("[PlayCount] unstar 参数空: userId={}, itemId={}, itemType={}", userId, itemId, itemType);
            return;
        }
        log.info("[PlayCount] unstar: userId={}, itemId={}, itemType={}", userId, itemId, itemType);
        userStarredMapper.delete(userId, itemId, itemType);
    }

    @Override
    public List<Long> getStarredIds(Long userId, String itemType) {
        if (userId == null || itemType == null) return List.of();
        return userStarredMapper.findStarredIds(userId, itemType);
    }


    @Override
    public void rate(Long userId, Long itemId, String itemType, int rating) {
        if (userId == null || itemId == null || itemType == null) return;
        if (rating < 1 || rating > 5) throw new IllegalArgumentException("评分范围 1-5");
        userRatingMapper.upsert(UserRating.builder()
                .userId(userId).itemId(itemId).itemType(itemType)
                .rating(rating).ratedAt(LocalDateTime.now()).build());
    }

    @Override
    public Map<Long, Integer> getUserRatings(Long userId, List<Long> songIds) {
        if (userId == null || songIds == null || songIds.isEmpty()) return Map.of();
        return userRatingMapper.batchGetUserRatings(userId, "song", songIds);
    }


    @Override
    public void deleteByItem(Long itemId, String itemType) {
        if (itemId == null || itemType == null) return;
        playCountMapper.deleteByItem(itemId, itemType);
        userStarredMapper.deleteByItem(itemId, itemType);
        userRatingMapper.deleteByItem(itemId, itemType);
    }

    @Override
    public void deleteBySongId(Long songId) {
        if (songId == null) return;
        playHistoryMapper.deleteBySongId(songId);
        deleteByItem(songId, "song");
    }
}
