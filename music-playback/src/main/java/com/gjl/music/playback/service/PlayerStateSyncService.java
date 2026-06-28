package com.gjl.music.playback.service;

import com.gjl.music.playback.model.PlayerState;

/**
 * 播放器状态同步服务接口。
 */
public interface PlayerStateSyncService {

    void broadcast(PlayerState state);

    PlayerState getLastState(String username);
}
