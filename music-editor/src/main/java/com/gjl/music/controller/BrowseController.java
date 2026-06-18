package com.gjl.music.controller;

import com.gjl.music.model.MusicMetadata;
import com.gjl.music.service.BrowseService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.Map;


@Slf4j
@RestController
@RequestMapping("/api/browse")
@PreAuthorize("hasAuthority('music:browse')")
public class BrowseController {

    private final BrowseService browseService;

    @Value("${music.covers-dir:../covers}")
    private String coversDir;

    public BrowseController(BrowseService browseService) {
        this.browseService = browseService;
    }

    @GetMapping("/directory")
    public ResponseEntity<?> listDirectory(@RequestParam(defaultValue = "") String rawPath) {
        try {
            return ResponseEntity.ok(browseService.listDirectory(rawPath));
        } catch (IllegalArgumentException | SecurityException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/metadata")
    public ResponseEntity<?> getMetadata(@RequestParam String rawPath) {
        try {
            MusicMetadata meta = browseService.getMetadata(rawPath);
            return ResponseEntity.ok(meta);
        } catch (IllegalArgumentException | SecurityException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("读取元数据失败: {}", rawPath, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "读取元数据失败: " + e.getMessage()));
        }
    }

    @GetMapping("/covers/**")
    public ResponseEntity<Resource> getCover(HttpServletRequest request) {
                String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String pattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String relativePath = new AntPathMatcher().extractPathWithinPattern(pattern, path);

                relativePath = UriUtils.decode(relativePath, StandardCharsets.UTF_8);
                if (relativePath.contains("..")) {
            return ResponseEntity.badRequest().build();
        }
        File dir = new File(coversDir).toPath().toAbsolutePath().normalize().toFile();
        File file = new File(dir, relativePath);
                try {
            if (!file.getCanonicalPath().startsWith(dir.getCanonicalPath() + File.separator)) {
                return ResponseEntity.badRequest().build();
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new FileSystemResource(file);
        String contentType = relativePath.endsWith(".png") ? "image/png"
                : relativePath.endsWith(".jpg") || relativePath.endsWith(".jpeg") ? "image/jpeg"
                : "application/octet-stream";
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(contentType)).body(resource);
    }

    @GetMapping("/directory-metadata")
    public ResponseEntity<?> getDirectoryMetadata(
            @RequestParam(defaultValue = "") String rawPath,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize) {
        try {
            return ResponseEntity.ok(browseService.getDirectoryMetadata(rawPath, page, pageSize));
        } catch (IllegalArgumentException | SecurityException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
