package com.gjl.music.playback.controller;

import com.gjl.music.playback.service.ArtistService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/artists")
public class ArtistController {

    private final ArtistService artistService;

    public ArtistController(ArtistService artistService) {
        this.artistService = artistService;
    }

    /** 列表查询：支持 sort=mostPlayed 按全局播放量排序 */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getArtists(
            @RequestParam(defaultValue = "alphabetical") String sort,
            @RequestParam(defaultValue = "12") int limit,
            Principal principal) {
        Long userId = artistService.resolveUserId(principal != null ? principal.getName() : null);
        if ("mostPlayed".equals(sort)) {
            return ResponseEntity.ok(artistService.getTopArtists(userId, limit));
        }
        // 其他 sort 类型暂不实现，返回空列表
        return ResponseEntity.ok(Map.of("artists", List.of(), "total", 0));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getArtist(@PathVariable Long id) {
        Map<String, Object> result = artistService.getArtist(id);
        if (result == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}/albums")
    public ResponseEntity<Map<String, Object>> getArtistAlbums(
            @PathVariable Long id,
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(required = false) String letter,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset,
            Principal principal) {
        Long userId = artistService.resolveUserId(principal != null ? principal.getName() : null);
        return ResponseEntity.ok(artistService.getArtistAlbums(id, sort, letter, limit, offset, userId));
    }

    @GetMapping("/{id}/songs")
    public ResponseEntity<Map<String, Object>> getArtistSongs(
            @PathVariable Long id,
            @RequestParam(required = false) String letter,
            @RequestParam(defaultValue = "alphabetical") String sort,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset,
            Principal principal) {
        Long userId = artistService.resolveUserId(principal != null ? principal.getName() : null);
        return ResponseEntity.ok(artistService.getArtistSongs(id, letter, sort, limit, offset, userId));
    }
}
