package com.gjl.music.controller.artist;

import com.gjl.music.service.artist.ArtistEditService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 艺术家编辑 REST API。
 * <p>
 * PUT /api/artists/{id} — 更新艺术家元数据并同步文件标签。
 * 与 music-playback 的 GET 端点共享同一 URL 路径，按 HTTP method 路由。
 */
@Slf4j
@RestController
@PreAuthorize("hasAuthority('artist:write')")
public class ArtistEditController {

    private final ArtistEditService artistEditService;

    public ArtistEditController(ArtistEditService artistEditService) {
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

    /**
     * 上传艺术家封面。图片保存到 coversDir，DB 同步更新。
     *
     * @param id   艺术家 ID
     * @param file 图片文件（仅限 image/*）
     * @return { coverUrl: "/api/browse/covers/artist-X-ts.ext" }
     */
    @PostMapping("/api/artists/{id}/cover")
    public ResponseEntity<?> uploadCover(@PathVariable Long id,
                                          @RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "文件为空"));
            }
            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                return ResponseEntity.badRequest().body(Map.of("error", "仅支持图片格式"));
            }
            String coverUrl = artistEditService.uploadCover(id, file);
            return ResponseEntity.ok(Map.of("coverUrl", coverUrl));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("封面上传失败: id={}", id, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "封面上传失败: " + e.getMessage()));
        }
    }
}
