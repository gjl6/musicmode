package com.gjl.music.playback.controller;

import com.gjl.music.playback.service.StreamingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;


@Slf4j
@RestController
@RequestMapping("/api/player")
@PreAuthorize("hasAuthority('music:browse')")
public class StreamController {

    private final StreamingService streamingService;

    public StreamController(StreamingService streamingService) {
        this.streamingService = streamingService;
    }


    @GetMapping("/stream")
    public void streamByPath(@RequestParam("rawPath") String rawPath,
                             HttpServletRequest request,
                             HttpServletResponse response) throws IOException {
        String path = URLDecoder.decode(rawPath, StandardCharsets.UTF_8);
        log.info("[StreamController] 播放请求(rawPath): path={}, Range={}",
                path, request.getHeader("Range"));
        streamingService.streamFile(path, request, response);
    }


    @GetMapping("/song/{songId}")
    public void streamById(@PathVariable Long songId,
                           HttpServletRequest request,
                           HttpServletResponse response) throws IOException {
        log.info("[StreamController] 播放请求(songId): songId={}, Range={}",
                songId, request.getHeader("Range"));
        streamingService.streamSong(songId, request, response);
    }
}
