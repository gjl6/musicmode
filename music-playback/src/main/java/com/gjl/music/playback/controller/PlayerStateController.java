package com.gjl.music.playback.controller;
import com.gjl.music.playback.service.PlayerStateSyncService;

import com.gjl.music.playback.model.PlayerState;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;


@RestController
@RequestMapping("/api/player/state")
@PreAuthorize("hasAuthority('music:browse')")
public class PlayerStateController {

    private final PlayerStateSyncService syncService;

    public PlayerStateController(PlayerStateSyncService syncService) {
        this.syncService = syncService;
    }


    @PostMapping
    public ResponseEntity<Void> report(@RequestBody PlayerState state, Principal principal) {
        state.setUsername(principal.getName());
        syncService.broadcast(state);
        return ResponseEntity.ok().build();
    }


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


    @GetMapping("/{username}")
    public ResponseEntity<PlayerState> getStateByUsername(@PathVariable String username) {
        PlayerState state = syncService.getLastState(username);
        if (state == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(state);
    }


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
