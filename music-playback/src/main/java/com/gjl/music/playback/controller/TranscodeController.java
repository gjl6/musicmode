package com.gjl.music.playback.controller;
import com.gjl.music.playback.service.TranscodeService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.security.Principal;


@Slf4j
@RestController
public class TranscodeController {

    private final TranscodeService transcodeService;

    public TranscodeController(TranscodeService transcodeService) {
        this.transcodeService = transcodeService;
    }


    @GetMapping("/rest/stream")
    @PreAuthorize("hasAuthority('music:browse')")
    public void stream(@RequestParam("id") Long songId,
                       @RequestParam(value = "maxBitRate", defaultValue = "0") int maxBitRate,
                       @RequestParam(value = "format", defaultValue = "") String format,
                       @RequestParam(value = "timeOffset", defaultValue = "0") int timeOffset,
                       HttpServletRequest request,
                       HttpServletResponse response,
                       Principal principal) throws IOException {

        String username = principal != null ? principal.getName() : "anonymous";
        log.info("[Transcode] 转码请求: songId={}, format={}, maxBitRate={}kbps, timeOffset={}s, user={}",
                songId, format, maxBitRate, timeOffset, username);
        transcodeService.stream(songId, format, maxBitRate, timeOffset,
                request, response, username);
    }
}
