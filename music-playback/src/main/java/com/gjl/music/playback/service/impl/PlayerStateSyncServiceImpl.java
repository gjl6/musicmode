package com.gjl.music.playback.service.impl;

import com.gjl.music.playback.model.PlayerState;
import com.gjl.music.playback.service.PlayerStateSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@Slf4j
@Service
public class PlayerStateSyncServiceImpl implements PlayerStateSyncService {

    private final SimpMessagingTemplate messaging;
    private final Map<String, PlayerState> lastStates = new ConcurrentHashMap<>();

    public PlayerStateSyncServiceImpl(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }


    @Override
    public void broadcast(PlayerState state) {
        String username = state.getUsername();
        if (username == null) return;

        state.setTimestamp(System.currentTimeMillis());
        lastStates.put(username, state);

                messaging.convertAndSend("/topic/player/" + username + "/state", state);
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


    @Override
    public PlayerState getLastState(String username) {
        return lastStates.get(username);
    }
}
