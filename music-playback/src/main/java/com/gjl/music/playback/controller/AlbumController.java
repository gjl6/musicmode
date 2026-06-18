package com.gjl.music.playback.controller;

import com.gjl.music.playback.service.AlbumService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/albums")
public class AlbumController {

    private final AlbumService albumService;

    public AlbumController(AlbumService albumService) {
        this.albumService = albumService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAlbums(
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(required = false) String letter,
            @RequestParam(required = false) Boolean starred,
            @RequestParam(defaultValue = "60") int limit,
            @RequestParam(defaultValue = "0") int offset,
            Principal principal) {
        String username = (starred != null && starred && principal != null)
                ? principal.getName() : null;
        return ResponseEntity.ok(albumService.getAlbums(sort, letter, starred, limit, offset, username));
    }

    @GetMapping("/letters")
    public ResponseEntity<Map<String, Object>> getAlbumLetters(
            @RequestParam(required = false) Boolean starred,
            Principal principal) {
        String username = (starred != null && starred && principal != null)
                ? principal.getName() : null;
        return ResponseEntity.ok(albumService.getAlbumLetters(starred, username));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getAlbum(@PathVariable Long id) {
        Map<String, Object> result = albumService.getAlbum(id);
        if (result == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}/songs")
    public ResponseEntity<Map<String, Object>> getAlbumSongs(@PathVariable Long id) {
        return ResponseEntity.ok(albumService.getAlbumSongs(id));
    }
}
