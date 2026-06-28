package com.gjl.music.playback.service.impl;
import com.gjl.music.playback.infra.streaming.RangeParser;
import com.gjl.music.playback.service.StreamingService;

import com.gjl.music.infra.util.ContentTypeResolver;
import com.gjl.music.mapper.SongMapper;
import com.gjl.music.model.Song;
import com.gjl.music.playback.model.Range;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * 音频流式传输服务实现。
 *
 * <h3>Range 请求处理</h3>
 * <ul>
 *   <li>无 Range 头 → 200 OK + 全量传输</li>
 *   <li>有效 Range → 206 Partial Content + Content-Range</li>
 *   <li>无效 Range → 416 Range Not Satisfiable</li>
 *   <li>If-None-Match (ETag) → 304 Not Modified</li>
 * </ul>
 *
 * <p>使用 8KB ByteBuffer + FileChannel 循环写入，避免全量加载到内存。</p>
 */
@Slf4j
@Service
public class StreamingServiceImpl implements StreamingService {

    private static final int BUFFER_SIZE = 8192;
    private static final int MAX_ETAG_CACHE_SIZE = 1000;

    private final SongMapper songMapper;

    public StreamingServiceImpl(SongMapper songMapper) {
        this.songMapper = songMapper;
    }

    @Override
    public void streamFile(String filePath, HttpServletRequest request,
                           HttpServletResponse response) throws IOException {
        Path path = Path.of(filePath);
        log.info("[StreamingService] 请求文件: {}", filePath);
        if (!Files.isRegularFile(path)) {
            log.warn("[StreamingService] 文件不存在: {}", filePath);
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "文件不存在: " + filePath);
            return;
        }

        long fileSize = Files.size(path);
        String contentType = ContentTypeResolver.resolve(filePath);
        String eTag = buildETag(filePath, fileSize);

        // —— ETag / If-None-Match → 304 ——
        String ifNoneMatch = request.getHeader("If-None-Match");
        if (ifNoneMatch != null && ifNoneMatch.equals(eTag)) {
            response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
            response.setHeader("ETag", eTag);
            return;
        }

        // —— Range 解析 ——
        String rangeHeader = request.getHeader("Range");
        Range range = RangeParser.parse(rangeHeader, fileSize);

        if (range != null && range.isUnsatisfiable()) {
            // 416 Range Not Satisfiable
            response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
            response.setHeader("Content-Range", "bytes */" + fileSize);
            return;
        }

        response.setContentType(contentType);
        response.setHeader("ETag", eTag);
        response.setHeader("Accept-Ranges", "bytes");
        response.setHeader("X-Content-Type-Options", "nosniff");

        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            if (range != null) {
                // 206 Partial Content
                long start = range.getStart();
                long length = range.getLength();
                response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
                response.setHeader("Content-Range", range.toContentRangeHeader(fileSize));
                response.setHeader("Content-Length", String.valueOf(length));
                channel.position(start);
                writeRange(channel, response.getOutputStream(), start, length);
            } else {
                // 200 OK — 全量
                response.setHeader("Content-Length", String.valueOf(fileSize));
                writeFull(channel, response.getOutputStream());
            }
        }
    }

    @Override
    public void streamSong(Long songId, HttpServletRequest request,
                           HttpServletResponse response) throws IOException {
        log.info("[StreamingService] 按ID查询歌曲: songId={}", songId);
        Song song = songMapper.findSongById(songId);
        if (song == null || song.getFilePath() == null) {
            log.warn("[StreamingService] 歌曲不存在: songId={}", songId);
            response.sendError(HttpServletResponse.SC_NOT_FOUND,
                    "歌曲不存在: id=" + songId);
            return;
        }
        log.info("[StreamingService] 找到歌曲: id={}, title={}, path={}",
                song.getId(), song.getTitle(), song.getFilePath());
        streamFile(song.getFilePath(), request, response);
    }

    // ── private helpers ──

    /** 全量写入（200 OK） */
    private void writeFull(FileChannel channel, OutputStream out) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(BUFFER_SIZE);
        while (channel.read(buf) != -1) {
            buf.flip();
            out.write(buf.array(), 0, buf.limit());
            buf.clear();
        }
        out.flush();
    }

    /** 范围写入（206 Partial Content） */
    private void writeRange(FileChannel channel, OutputStream out,
                            long start, long length) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(BUFFER_SIZE);
        long remaining = length;
        while (remaining > 0) {
            int toRead = (int) Math.min(BUFFER_SIZE, remaining);
            buf.limit(toRead);
            int bytesRead = channel.read(buf);
            if (bytesRead == -1) break;
            buf.flip();
            out.write(buf.array(), 0, buf.limit());
            buf.clear();
            remaining -= bytesRead;
        }
        out.flush();
    }

    /** 构建 ETag：基于路径 + 大小的简单哈希 */
    private String buildETag(String filePath, long fileSize) {
        // 使用 hashCode 构建简单 ETag（避免对每个请求做 I/O）
        int hash = filePath.hashCode() ^ Long.hashCode(fileSize);
        return '"' + Integer.toHexString(hash) + '"';
    }
}
