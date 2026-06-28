package com.gjl.music.controller.song;

import com.gjl.music.service.song.StyleEditService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 风格（流派）编辑 REST API。
 * <p>
 * PUT /api/genres/{name} — 更新风格元数据并同步文件标签。
 * 这是全新端点：此前 Style/Genre 没有任何编辑能力。
 * 与 music-playback 的 GET /api/genres/{name} 共享路径，按 HTTP method 路由。
 */
@Slf4j
@RestController
@PreAuthorize("hasAuthority('music:write')")
public class StyleEditController {

    private final StyleEditService styleEditService;

    public StyleEditController(StyleEditService styleEditService) {
        this.styleEditService = styleEditService;
    }

    @PutMapping("/api/genres/{name}")
    public ResponseEntity<?> updateStyle(@PathVariable String name,
                                          @RequestBody Map<String, Object> body) {
        try {
            Map<String, Object> result = styleEditService.updateStyle(name, body);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("更新风格失败: name={}", name, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "更新风格失败: " + e.getMessage()));
        }
    }
}
