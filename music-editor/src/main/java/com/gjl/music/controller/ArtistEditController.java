package com.gjl.music.controller;

import com.gjl.music.service.ArtistEditServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;


@Slf4j
@RestController
@PreAuthorize("hasAuthority('music:edit')")
public class ArtistEditController {

    private final ArtistEditServiceImpl artistEditService;

    public ArtistEditController(ArtistEditServiceImpl artistEditService) {
        this.artistEditService = artistEditService;
    }

    @PutMapping("/api/artists/{id}")
    public ResponseEntity<?> updateArtist(@PathVariable Long id,
                                           @RequestBody Map<String, Object> body) {
        try {
            Map<String, Object> result = artistEditService.updateArtist(id, body);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("更新艺术家失败: id={}", id, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "更新艺术家失败: " + e.getMessage()));
        }
    }
