package com.gjl.music.playback.service.impl;

import com.gjl.music.model.Song;
import com.gjl.music.playback.mapper.PlayQueueMapper;
import com.gjl.music.playback.model.PlayQueueEntry;
import com.gjl.music.playback.service.PlayQueueService;
import com.gjl.music.mapper.MusicMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;


@Slf4j
@Service
public class PlayQueueServiceImpl implements PlayQueueService {

    private final PlayQueueMapper queueMapper;
    private final MusicMapper musicMapper;

    public PlayQueueServiceImpl(PlayQueueMapper queueMapper, MusicMapper musicMapper) {
        this.queueMapper = queueMapper;
        this.musicMapper = musicMapper;
    }


    @Override
    public List<Song> getQueue(String username) {
        List<PlayQueueEntry> entries = queueMapper.findByUsername(username);
        if (entries.isEmpty()) return List.of();

        List<Long> songIds = entries.stream()
                .map(PlayQueueEntry::getSongId)
                .toList();
        return musicMapper.findSongsByIds(songIds);
    }


    @Override
    public List<PlayQueueEntry> getEntries(String username) {
        return queueMapper.findByUsername(username);
    }


    @Override
    @Transactional
    public void saveQueue(String username, List<Long> songIds) {
        queueMapper.clearByUsername(username);

        List<PlayQueueEntry> entries = new ArrayList<>();
        int pos = 0;
        for (Long songId : songIds) {
            entries.add(PlayQueueEntry.builder()
                    .username(username)
                    .songId(songId)
                    .position(pos++)
                    .build());
        }
        if (!entries.isEmpty()) {
            queueMapper.batchInsert(entries);
        }
        log.debug("播放队列保存: user={}, tracks={}", username, songIds.size());
    }


    @Override
    @Transactional
    public void remove(String username, int position) {
        queueMapper.deleteByUsernameAndPosition(username, position);
    }


    @Override
    @Transactional
    public void clear(String username) {
        queueMapper.clearByUsername(username);
    }
}
