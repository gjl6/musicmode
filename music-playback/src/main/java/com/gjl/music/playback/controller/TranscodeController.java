package com.gjl.music.playback.controller;
import com.gjl.music.playback.service.TranscodeService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.security.Principal;

/**
 * 转码流媒体 REST API（Subsonic 兼容参数）。
 *
 * <h3>端点</h3>
 * <ul>
 *   <li>{@code GET /rest/stream?id={songId}&maxBitRate={kbps}&format={mp3|raw}&timeOffset={sec}}</li>
 * </ul>
 *
 * <p>同时提供 Web 前端使用的简化端点。</p>
 */
@Slf4j
@RestController
public class TranscodeController {

    private final TranscodeService transcodeService;

    public TranscodeController(TranscodeService transcodeService) {
        this.transcodeService = transcodeService;
    }

    /**
     * Subsonic 兼容流媒体端点。
     *
     * <p>参数：
     * <ul>
     *   <li>{@code id} — 歌曲 ID（必填）</li>
     *   <li>{@code maxBitRate} — 最大比特率（kbps），0 = 不限</li>
     *   <li>{@code format} — 目标格式（mp3/opus/aac/flac/raw），空/raw = 原始</li>
     *   <li>{@code timeOffset} — 时间偏移（秒），0 = 从头开始</li>
     * </ul>
     */
    @GetMapping("/api/stream/transcode")
    @PreAuthorize("hasAuthority('music:play')")
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
