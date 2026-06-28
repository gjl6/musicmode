package com.gjl.music.controller.song;

import com.gjl.music.dto.SaveMetadataRequest;
import com.gjl.music.model.MusicMetadata;
import com.gjl.music.service.song.BrowseService;
import com.gjl.music.service.song.MusicEditService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 元数据编辑 REST API —— 单文件 CRUD 写操作。
 *
 * <p>端点：
 * <ul>
 *   <li>POST /api/music/save     — 保存字段编辑</li>
 *   <li>GET  /api/music/metadata — 单文件完整元数据</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/music")
@PreAuthorize("hasAuthority('music:write')")
public class MusicEditController {

    private final MusicEditService musicEditService;
    private final BrowseService browseService;

    public MusicEditController(MusicEditService musicEditService, BrowseService browseService) {
        this.musicEditService = musicEditService;
        this.browseService = browseService;
    }

    @PostMapping("/save")
    public ResponseEntity<?> save(@RequestBody SaveMetadataRequest request) {
        try {
            MusicMetadata result = musicEditService.saveFields(
                    request.path(), request.metadata());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("保存元数据失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "保存失败: " + e.getMessage()));
        }
    }

    @PostMapping("/download-cover")
    public ResponseEntity<?> downloadCover(@RequestBody Map<String, String> request) {
        try {
            String url = request.get("url");
            if (url == null || url.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "url 参数不能为空"));
            }
            String coverPath = musicEditService.downloadCover(url);
            return ResponseEntity.ok(Map.of("coverPath", coverPath));
        } catch (Exception e) {
            log.error("下载封面失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/metadata")
    public ResponseEntity<?> getMetadata(@RequestParam(defaultValue = "") String rawPath) {
        try {
            MusicMetadata meta = browseService.getMetadata(rawPath);
            return ResponseEntity.ok(meta);
        } catch (Exception e) {
            log.error("获取元数据失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "获取元数据失败: " + e.getMessage()));
        }
    }
}
