package com.gjl.music.playback.controller;
import com.gjl.music.playback.service.PlayQueueService;

import com.gjl.music.model.Song;
import com.gjl.music.playback.model.PlayQueueEntry;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/api/play-queue")
@PreAuthorize("hasAuthority('music:browse')")
public class PlayQueueController {

    private final PlayQueueService playQueueService;

    public PlayQueueController(PlayQueueService playQueueService) {
        this.playQueueService = playQueueService;
    }


    @GetMapping
    public List<Song> getQueue(Principal principal) {
        return playQueueService.getQueue(principal.getName());
    }


    @GetMapping("/entries")
    public List<PlayQueueEntry> getEntries(Principal principal) {
        return playQueueService.getEntries(principal.getName());
    }


    @PostMapping
    public ResponseEntity<Void> save(@RequestBody Map<String, Object> body,
                                     Principal principal) {
        @SuppressWarnings("unchecked")
        List<Long> songIds = ((List<?>) body.get("songIds")).stream()
                .map(o -> ((Number) o).longValue())
                .toList();
        playQueueService.saveQueue(principal.getName(), songIds);
        return ResponseEntity.ok().build();
    }


    @DeleteMapping("/{position}")
    public ResponseEntity<Void> remove(@PathVariable int position, Principal principal) {
        playQueueService.remove(principal.getName(), position);
        return ResponseEntity.noContent().build();
    }


    @DeleteMapping
    public ResponseEntity<Void> clear(Principal principal) {
        playQueueService.clear(principal.getName());
        return ResponseEntity.noContent().build();
    }
}
