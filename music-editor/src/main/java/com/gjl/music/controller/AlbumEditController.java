package com.gjl.music.controller;

import com.gjl.music.service.AlbumEditServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;


@Slf4j
@RestController
@PreAuthorize("hasAuthority('music:edit')")
public class AlbumEditController {

    private final AlbumEditServiceImpl albumEditService;

    public AlbumEditController(AlbumEditServiceImpl albumEditService) {
        this.albumEditService = albumEditService;
    }

    @PutMapping("/api/albums/{id}")
    public ResponseEntity<?> updateAlbum(@PathVariable Long id,
                                          @RequestBody Map<String, Object> body) {
        try {
            Map<String, Object> result = albumEditService.updateAlbum(id, body);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("更新专辑失败: id={}", id, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "更新专辑失败: " + e.getMessage()));
        }
    }
}
