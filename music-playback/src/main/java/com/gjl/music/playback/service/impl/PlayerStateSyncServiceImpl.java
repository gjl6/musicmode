package com.gjl.music.playback.service.impl;

import com.gjl.music.playback.model.PlayerState;
import com.gjl.music.playback.service.PlayerStateSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 播放器状态同步服务。
 *
 * <p>通过 STOMP WebSocket 向订阅客户端推送播放状态。
 *
 * <h3>推送通道</h3>
 * <ul>
 *   <li>{@code /topic/player/{username}/state} — 全量状态快照</li>
 *   <li>{@code /topic/player/{username}/event} — 离散事件（PLAY/PAUSE/STOP/TRACK_CHANGE）</li>
 * </ul>
 */
@Slf4j
@Service
public class PlayerStateSyncServiceImpl implements PlayerStateSyncService {

    private final SimpMessagingTemplate messaging;
    private final Map<String, PlayerState> lastStates = new ConcurrentHashMap<>();

    public PlayerStateSyncServiceImpl(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    /**
     * 广播播放器状态变更。
     *
     * @param state 当前播放器状态
     */
    @Override
    public void broadcast(PlayerState state) {
        String username = state.getUsername();
        if (username == null) return;

        state.setTimestamp(System.currentTimeMillis());
        lastStates.put(username, state);

        // 推送全量状态
        messaging.convertAndSend("/topic/player/" + username + "/state", state);
        // 推送离散事件
        if (state.getEvent() != null) {
            messaging.send(
                    "/topic/player/" + username + "/event",
                    org.springframework.messaging.support.MessageBuilder.withPayload(
                            Map.of(
                                    "event", state.getEvent(),
                                    "songId", state.getCurrentSongId() != null
                                            ? state.getCurrentSongId() : -1,
                                    "position", state.getPosition(),
                                    "timestamp", state.getTimestamp()
                            )).build());
        }

        log.debug("播放器状态推送: user={}, event={}, songId={}, position={}s",
                username, state.getEvent(), state.getCurrentSongId(), state.getPosition());
    }

    /**
     * 获取指定用户最后一次已知播放状态。
     */
    @Override
    public PlayerState getLastState(String username) {
        return lastStates.get(username);
    }
}
