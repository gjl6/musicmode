package com.gjl.music.controller;

import com.gjl.music.infra.util.ContentTypeResolver;
import com.gjl.music.pipeline.PipelineFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;


@Slf4j
@RestController
@RequestMapping("/api/player")
@PreAuthorize("hasAuthority('music:browse')")
public class PlayerController {

    private final PipelineFactory pipelineFactory;

    public PlayerController(PipelineFactory pipelineFactory) {
        this.pipelineFactory = pipelineFactory;
    }

    @GetMapping("/stream/{fileId}")
    public ResponseEntity<Resource> stream(@PathVariable String fileId,
                                           @RequestParam(defaultValue = "") String rawPath) {
                String path = URLDecoder.decode(rawPath, StandardCharsets.UTF_8);
        log.info("[PlayerController] 播放请求: fileId={}, rawPath={}", fileId, path);
        File file = pipelineFactory.resolvePath(path).toFile();

        if (!file.exists() || !file.isFile()) {
            log.warn("[PlayerController] 文件不存在: {}", file.getAbsolutePath());
            return ResponseEntity.notFound().build();
        }

        log.info("[PlayerController] 开始流式传输: {} ({} bytes)", file.getName(), file.length());
        Resource resource = new FileSystemResource(file);
        String contentType = ContentTypeResolver.resolve(file.getName());

                ContentDisposition disposition = ContentDisposition.inline()
                .filename(file.getName(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .body(resource);
    }
}
