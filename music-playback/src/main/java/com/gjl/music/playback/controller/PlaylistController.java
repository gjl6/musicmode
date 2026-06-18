package com.gjl.music.playback.controller;
import com.gjl.music.playback.infra.m3u.M3uParser;
import com.gjl.music.playback.service.PlayQueueService;
import com.gjl.music.playback.service.PlaylistService;

import com.gjl.music.model.Song;
import com.gjl.music.playback.model.Playlist;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.*;


@RestController
@RequestMapping("/api/playlists")
@PreAuthorize("hasAuthority('music:browse')")
public class PlaylistController {

    private final PlaylistService playlistService;
    private final PlayQueueService playQueueService;

    public PlaylistController(PlaylistService playlistService,
                              PlayQueueService playQueueService) {
        this.playlistService = playlistService;
        this.playQueueService = playQueueService;
    }


    @GetMapping
    public List<Playlist> list(Principal principal) {
        return playlistService.listByUsername(principal.getName());
    }


    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getById(@PathVariable Long id) {
        Playlist pl = playlistService.getById(id);
        if (pl == null) return ResponseEntity.notFound().build();
        List<Song> songs = playlistService.getSongs(id);
        return ResponseEntity.ok(Map.of("playlist", pl, "songs", songs));
    }


    @PostMapping
    public Playlist create(@RequestBody Map<String, Object> body, Principal principal) {
        String name = (String) body.get("name");
        String comment = (String) body.getOrDefault("comment", "");
        String coverPath = (String) body.getOrDefault("coverPath", null);
        boolean isPublic = (boolean) body.getOrDefault("isPublic", false);
        Long userId = body.get("ownerId") instanceof Number n ? n.longValue() : null;
        return playlistService.createByUsername(name, comment, principal.getName(), userId,
                isPublic, coverPath);
    }


    @PostMapping("/{id}/cover")
    public ResponseEntity<?> uploadCover(@PathVariable Long id,
                                         @RequestParam("file") MultipartFile file)
            throws IOException {
        try {
            String coverPath = playlistService.uploadCover(id, file);
            return ResponseEntity.ok(Map.of("coverPath", coverPath));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }


    @PutMapping("/{id}")
    public ResponseEntity<Playlist> update(@PathVariable Long id,
                                           @RequestBody Map<String, Object> body) {
        try {
            String name = (String) body.get("name");
            String comment = (String) body.getOrDefault("comment", "");
            String coverPath = (String) body.getOrDefault("coverPath", null);
            boolean isPublic = (boolean) body.getOrDefault("isPublic", false);
            return ResponseEntity.ok(playlistService.update(id, name, comment, isPublic,
                    coverPath));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (org.springframework.security.access.AccessDeniedException e) {
            return ResponseEntity.status(403).build();
        }
    }


    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        try {
            playlistService.delete(id);
            return ResponseEntity.noContent().build();
        } catch (org.springframework.security.access.AccessDeniedException e) {
            return ResponseEntity.status(403).build();
        }
    }


    @PostMapping("/{id}/songs")
    public ResponseEntity<?> addSongs(@PathVariable Long id,
                                       @RequestBody Map<String, Object> body) {
        try {
            @SuppressWarnings("unchecked")
            List<Long> songIds = ((List<?>) body.get("songIds")).stream()
                    .map(o -> o instanceof Number n ? n.longValue() : Long.parseLong(o.toString()))
                    .toList();
            playlistService.addSongs(id, songIds);
            return ResponseEntity.ok(Map.of("added", songIds.size()));
        } catch (org.springframework.security.access.AccessDeniedException e) {
            return ResponseEntity.status(403).build();
        }
    }


    @DeleteMapping("/{id}/songs/{position}")
    public ResponseEntity<Void> removeSong(@PathVariable Long id,
                                           @PathVariable int position) {
        try {
            playlistService.removeSong(id, position);
            return ResponseEntity.noContent().build();
        } catch (org.springframework.security.access.AccessDeniedException e) {
            return ResponseEntity.status(403).build();
        }
    }


    @PutMapping("/{id}/tracks/{position}/move")
    public ResponseEntity<?> reorderSong(@PathVariable Long id,
                                          @PathVariable int position,
                                          @RequestParam("to") int toPosition) {
        try {
            playlistService.reorderSong(id, position, toPosition);
            return ResponseEntity.ok(Map.of("moved", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (org.springframework.security.access.AccessDeniedException e) {
            return ResponseEntity.status(403).build();
        }
    }


    @PutMapping("/{id}/tracks")
    public ResponseEntity<?> reorderAll(@PathVariable Long id,
                                         @RequestBody Map<String, Object> body) {
        try {
            @SuppressWarnings("unchecked")
            List<Long> songIds = ((List<?>) body.get("songIds")).stream()
                    .map(o -> o instanceof Number n ? n.longValue() : Long.parseLong(o.toString()))
                    .toList();
            playlistService.reorderAll(id, songIds);
            return ResponseEntity.ok(Map.of("saved", songIds.size()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (org.springframework.security.access.AccessDeniedException e) {
            return ResponseEntity.status(403).build();
        }
    }


    @GetMapping("/containing/{songId}")
    public ResponseEntity<List<Playlist>> getContainingPlaylists(@PathVariable Long songId,
                                                                   Principal principal) {
        return ResponseEntity.ok(
                playlistService.getPlaylistsContainingSong(principal.getName(), songId));
    }


    @GetMapping("/{id}/export.m3u")
    public ResponseEntity<String> exportM3u(@PathVariable Long id) {
        Playlist pl = playlistService.getById(id);
        if (pl == null) return ResponseEntity.notFound().build();
        String content = playlistService.exportM3u(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/x-mpegurl"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + pl.getName() + ".m3u8\"")
                .body(content);
    }


    @PostMapping("/{id}/import")
    public ResponseEntity<Map<String, Object>> importM3u(@PathVariable Long id,
                                                          @RequestParam("file") MultipartFile file,
                                                          @RequestParam(value = "rootDir", defaultValue = "") String rootDir)
            throws IOException {
        try {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            List<Long> songIds = playlistService.importM3u(id, content, rootDir);

            int total = M3uParser
                    .parse(content.startsWith("﻿") ? content.substring(1) : content).size();

            return ResponseEntity.ok(Map.of(
                    "total", total,
                    "imported", songIds.size(),
                    "songIds", songIds));
        } catch (org.springframework.security.access.AccessDeniedException e) {
            return ResponseEntity.status(403).build();
        }
    }
}
