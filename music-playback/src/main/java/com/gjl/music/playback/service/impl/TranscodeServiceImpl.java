package com.gjl.music.playback.service.impl;

import com.gjl.music.infra.util.FfmpegUtil;
import com.gjl.music.mapper.MusicMapper;
import com.gjl.music.model.Song;
import com.gjl.music.playback.config.PlaybackProperties;
import com.gjl.music.playback.infra.transcoding.TranscodeCommandBuilder;
import com.gjl.music.playback.infra.transcoding.TranscodeDecider;
import com.gjl.music.playback.infra.transcoding.TranscodeLimiter;
import com.gjl.music.playback.infra.transcoding.TranscodingCache;
import com.gjl.music.playback.model.TranscodeDecision;
import com.gjl.music.playback.service.TranscodeService;
import com.gjl.music.infra.util.ContentTypeResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;


@Slf4j
@Service
public class TranscodeServiceImpl implements TranscodeService {

    private final MusicMapper musicMapper;
    private final TranscodeDecider decider;
    private final TranscodeCommandBuilder commandBuilder;
    private final TranscodingCache cache;
    private final TranscodeLimiter limiter;
    private final PlaybackProperties properties;

    public TranscodeServiceImpl(MusicMapper musicMapper,
                                TranscodeDecider decider,
                                TranscodeCommandBuilder commandBuilder,
                                TranscodingCache cache,
                                TranscodeLimiter limiter,
                                PlaybackProperties properties) {
        this.musicMapper = musicMapper;
        this.decider = decider;
        this.commandBuilder = commandBuilder;
        this.cache = cache;
        this.limiter = limiter;
        this.properties = properties;
    }


    @Override
    public void stream(Long songId, String reqFormat, int maxBitRate, int timeOffset,
                       HttpServletRequest request, HttpServletResponse response,
                       String username) throws IOException {
        Song song = musicMapper.findSongById(songId);
        if (song == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "歌曲不存在: " + songId);
            return;
        }

        Path sourcePath = Path.of(song.getFilePath());
        if (!java.nio.file.Files.isRegularFile(sourcePath)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND,
                    "文件不存在: " + song.getFilePath());
            return;
        }

        if (!properties.getTranscoding().isEnabled()) {
                        serveRawFile(sourcePath, response);
            return;
        }

        TranscodeDecision decision = decider.decide(song, reqFormat, maxBitRate);

        if (decision.canDirectPlay()) {
                        serveRawFile(sourcePath, response);
            return;
        }

                if (!limiter.tryAcquire(username)) {
            response.setStatus(429);
            response.setHeader("Retry-After", "5");
            response.getWriter().write("{\"error\": \"转码并发已达上限，请稍后重试\"}");
            return;
        }

        try {
            String cacheKey = buildCacheKey(song, decision, timeOffset);
            Path cached = cache.getOrCompute(cacheKey, () -> {
                List<String> cmd = commandBuilder.build(sourcePath, decision, timeOffset);
                return FfmpegUtil.executeAndPipe(cmd);
            });

                        response.setContentType(decision.targetMimeType());
            response.setHeader("Accept-Ranges", "none");
            response.setHeader("X-Content-Type-Options", "nosniff");
            if (song.getDuration() != null) {
                response.setHeader("X-Content-Duration", String.valueOf(song.getDuration()));
            }

            try (InputStream in = java.nio.file.Files.newInputStream(cached)) {
                in.transferTo(response.getOutputStream());
            }
        } finally {
            limiter.release(username);
        }
    }


    private void serveRawFile(Path path, HttpServletResponse response) throws IOException {
        response.setContentType(ContentTypeResolver.resolve(
                path.getFileName().toString()));
        response.setHeader("Accept-Ranges", "bytes");
        long size = java.nio.file.Files.size(path);
        response.setHeader("Content-Length", String.valueOf(size));

        try (InputStream in = java.nio.file.Files.newInputStream(path);
             OutputStream out = response.getOutputStream()) {
            in.transferTo(out);
        }
    }


    private String buildCacheKey(Song song, TranscodeDecision decision, int offset) {
        String updatedAt = song.getUpdateTime() != null
                ? String.valueOf(song.getUpdateTime().toEpochSecond(
                        java.time.ZoneOffset.UTC)) : "0";
        return String.format("%s.%s.%d.%d.%d.%s.%d",
                song.getId(),
                updatedAt,
                decision.targetBitrate(),
                song.getSampleRate() != null ? song.getSampleRate() : 0,
                song.getChannels() != null ? song.getChannels() : 0,
                decision.targetFormat(),
                offset);
    }
}
