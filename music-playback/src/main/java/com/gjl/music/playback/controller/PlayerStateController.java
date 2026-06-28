package com.gjl.music.playback.controller;
import com.gjl.music.playback.service.PlayerStateSyncService;

import com.gjl.music.playback.model.PlayerState;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

/**
 * 播放器状态 REST API。
 *
 * <p>客户端通过此 API 上报播放状态，并查询其他客户端的状态。
 */
@RestController
@RequestMapping("/api/player/state")
@PreAuthorize("hasAuthority('music:play')")
public class PlayerStateController {

    private final PlayerStateSyncService syncService;

    public PlayerStateController(PlayerStateSyncService syncService) {
        this.syncService = syncService;
    }

    /**
     * 上报播放状态（客户端定时发送）。
     */
    @PostMapping
    public ResponseEntity<Void> report(@RequestBody PlayerState state, Principal principal) {
        state.setUsername(principal.getName());
        syncService.broadcast(state);
        return ResponseEntity.ok().build();
    }

    /**
     * 获取当前用户最后一次已知状态。
     */
    @GetMapping
    public ResponseEntity<PlayerState> getLastState(Principal principal) {
        PlayerState state = syncService.getLastState(principal.getName());
        if (state == null) {
            PlayerState empty = PlayerState.builder()
                    .username(principal.getName())
                    .event("STOP")
                    .position(0)
                    .queueIndex(-1)
                    .build();
            return ResponseEntity.ok(empty);
        }
        return ResponseEntity.ok(state);
    }

    /**
     * 获取指定用户最后一次已知状态（跨设备）。
     */
    @GetMapping("/{username}")
    public ResponseEntity<PlayerState> getStateByUsername(@PathVariable String username) {
        PlayerState state = syncService.getLastState(username);
        if (state == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(state);
    }

    /**
     * 发送播放控制事件（play/pause/stop/seek）。
     */
    @PostMapping("/control")
    public ResponseEntity<Void> control(@RequestBody Map<String, Object> body,
                                        Principal principal) {
        String event = (String) body.get("event");
        if (event == null) return ResponseEntity.badRequest().build();

        PlayerState state = PlayerState.builder()
                .username(principal.getName())
                .event(event.toUpperCase())
                .currentSongId(body.get("songId") != null
                        ? ((Number) body.get("songId")).longValue() : null)
                .position(body.get("position") != null
                        ? ((Number) body.get("position")).doubleValue() : 0)
                .build();

        syncService.broadcast(state);
        return ResponseEntity.ok().build();
    }
}
