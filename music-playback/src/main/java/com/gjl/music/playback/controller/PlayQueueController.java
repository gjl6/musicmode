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

/**
 * 播放队列 REST API。
 */
@RestController
@RequestMapping("/api/play-queue")
@PreAuthorize("hasAuthority('music:play')")
public class PlayQueueController {

    private final PlayQueueService playQueueService;

    public PlayQueueController(PlayQueueService playQueueService) {
        this.playQueueService = playQueueService;
    }

    /** 获取当前用户的播放队列 */
    @GetMapping
    public List<Song> getQueue(Principal principal) {
        return playQueueService.getQueue(principal.getName());
    }

    /** 获取原始队列条目（含 position） */
    @GetMapping("/entries")
    public List<PlayQueueEntry> getEntries(Principal principal) {
        return playQueueService.getEntries(principal.getName());
    }

    /** 保存（替换）播放队列 */
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

    /** 从队列中移除指定位置 */
    @DeleteMapping("/{position}")
    public ResponseEntity<Void> remove(@PathVariable int position, Principal principal) {
        playQueueService.remove(principal.getName(), position);
        return ResponseEntity.noContent().build();
    }

    /** 清空队列 */
    @DeleteMapping
    public ResponseEntity<Void> clear(Principal principal) {
        playQueueService.clear(principal.getName());
        return ResponseEntity.noContent().build();
    }
}
