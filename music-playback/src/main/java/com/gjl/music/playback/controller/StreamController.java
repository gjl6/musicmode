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

/**
 * 音频流媒体 REST API。
 *
 * <p>替代旧 {@code PlayerController}，完整支持 HTTP Range（206/304/416）。
 *
 * <h3>端点</h3>
 * <ul>
 *   <li>{@code GET /api/player/stream?rawPath=...} — 原始路径流式传输</li>
 *   <li>{@code GET /api/player/stream/{songId}} — 按歌曲 ID 流式传输</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/player")
@PreAuthorize("hasAuthority('music:play')")
public class StreamController {

    private final StreamingService streamingService;

    public StreamController(StreamingService streamingService) {
        this.streamingService = streamingService;
    }

    /**
     * 按原始文件路径流式传输。
     *
     * <p>兼容旧 PlayerController 的 {@code rawPath} 参数。
     *
     * @param rawPath  Base64 编码的文件路径（或原始路径）
     * @param request  HTTP 请求
     * @param response HTTP 响应
     */
    @GetMapping("/stream")
    public void streamByPath(@RequestParam("rawPath") String rawPath,
                             HttpServletRequest request,
                             HttpServletResponse response) throws IOException {
        String path = URLDecoder.decode(rawPath, StandardCharsets.UTF_8);
        log.info("[StreamController] 播放请求(rawPath): path={}, Range={}",
                path, request.getHeader("Range"));
        streamingService.streamFile(path, request, response);
    }

    /**
     * 按歌曲数据库 ID 流式传输。
     * 注意：路径用 /song/{songId} 避免与 music-core PlayerController 的 /stream/{fileId} 冲突
     *
     * @param songId 歌曲 ID
     */
    @GetMapping("/song/{songId}")
    public void streamById(@PathVariable Long songId,
                           HttpServletRequest request,
                           HttpServletResponse response) throws IOException {
        log.info("[StreamController] 播放请求(songId): songId={}, Range={}",
                songId, request.getHeader("Range"));
        streamingService.streamSong(songId, request, response);
    }
}
