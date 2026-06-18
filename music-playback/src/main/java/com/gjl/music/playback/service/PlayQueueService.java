package com.gjl.music.playback.service;

import com.gjl.music.model.Song;
import com.gjl.music.playback.model.PlayQueueEntry;

import java.util.List;


public interface PlayQueueService {

    List<Song> getQueue(String username);

    List<PlayQueueEntry> getEntries(String username);

    void saveQueue(String username, List<Long> songIds);

    void remove(String username, int position);

    void clear(String username);
}
