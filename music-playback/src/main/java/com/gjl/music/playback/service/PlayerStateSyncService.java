package com.gjl.music.playback.service;

import com.gjl.music.playback.model.PlayerState;


public interface PlayerStateSyncService {

    void broadcast(PlayerState state);

    PlayerState getLastState(String username);
}
