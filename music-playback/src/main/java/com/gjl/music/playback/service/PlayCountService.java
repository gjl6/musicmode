package com.gjl.music.playback.service;

import java.util.List;
import java.util.Map;


public interface PlayCountService {


    void scrobbleTransactional(Long userId, Long songId, Long albumId,
                               List<Long> artistIds, List<Long> styleIds, String source);


    Map<Long, Integer> getSongPlayCounts(Long userId, List<Long> songIds);


    void star(Long userId, Long itemId, String itemType);


    void unstar(Long userId, Long itemId, String itemType);


    List<Long> getStarredIds(Long userId, String itemType);


    void rate(Long userId, Long itemId, String itemType, int rating);


    Map<Long, Integer> getUserRatings(Long userId, List<Long> songIds);


    void deleteByItem(Long itemId, String itemType);


    void deleteBySongId(Long songId);
}
