package com.gjl.music.controller;

import com.gjl.music.service.StyleEditServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;


@Slf4j
@RestController
@PreAuthorize("hasAuthority('music:edit')")
public class StyleEditController {

    private final StyleEditServiceImpl styleEditService;

    public StyleEditController(StyleEditServiceImpl styleEditService) {
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
