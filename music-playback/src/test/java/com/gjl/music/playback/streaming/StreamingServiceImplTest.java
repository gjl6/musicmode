package com.gjl.music.playback.streaming;

import com.gjl.music.mapper.MusicMapper;
import com.gjl.music.model.Song;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("StreamingServiceImpl 单元测试")
class StreamingServiceImplTest {

    @Mock
    private MusicMapper musicMapper;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private StreamingServiceImpl service;
    private Path tempFile;


    private ByteArrayOutputStream capturedOutput;

    @BeforeEach
    void setUp() throws Exception {
        service = new StreamingServiceImpl(musicMapper);
        tempFile = Files.createTempFile("stream-test-", ".mp3");
        byte[] data = new byte[8192];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i % 256);
        }
        Files.write(tempFile, data);
    }

    @AfterEach
    void tearDown() throws Exception {
        Files.deleteIfExists(tempFile);
    }


    private ByteArrayOutputStream mockOutputStream() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        this.capturedOutput = baos;
        lenient().when(response.getOutputStream()).thenReturn(new ServletOutputStream() {
            @Override public void write(int b) { baos.write(b); }
            @Override public boolean isReady() { return true; }
            @Override public void setWriteListener(WriteListener wl) {}
        });
        return baos;
    }


    @Test
    @DisplayName("文件不存在 → 404")
    void fileNotFound() throws Exception {
        mockOutputStream();
        service.streamFile("/nonexistent/file.mp3", request, response);
        verify(response).sendError(HttpServletResponse.SC_NOT_FOUND,
                "文件不存在: /nonexistent/file.mp3");
    }


    @Test
    @DisplayName("If-None-Match 匹配 → 304 Not Modified")
    void etagNotModified() throws Exception {
        String eTag = '"' + Integer.toHexString(
                tempFile.toString().hashCode() ^ Long.hashCode(8192)) + '"';
        when(request.getHeader("If-None-Match")).thenReturn(eTag);

        service.streamFile(tempFile.toString(), request, response);

        verify(response).setStatus(HttpServletResponse.SC_NOT_MODIFIED);
        verify(response).setHeader("ETag", eTag);
    }


    @Test
    @DisplayName("无效 Range → 416")
    void unsatisfiableRange() throws Exception {
        when(request.getHeader("Range")).thenReturn("bytes=99999-99999");
        mockOutputStream();

        service.streamFile(tempFile.toString(), request, response);

        verify(response).setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
    }


    @Test
    @DisplayName("无 Range 头 → 200 OK + 全量传输")
    void fullStream200() throws Exception {
        ByteArrayOutputStream baos = mockOutputStream();

        service.streamFile(tempFile.toString(), request, response);

        verify(response).setContentType("audio/mpeg");
        verify(response).setHeader(eq("Accept-Ranges"), eq("bytes"));
        assertEquals(8192, baos.size());
    }


    @Test
    @DisplayName("有效 Range → 206 Partial Content")
    void partialContent206() throws Exception {
        ByteArrayOutputStream baos = mockOutputStream();
        when(request.getHeader("Range")).thenReturn("bytes=0-1023");

        service.streamFile(tempFile.toString(), request, response);

        verify(response).setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
        assertEquals(1024, baos.size());
    }

    @Test
    @DisplayName("Range bytes=1024- → 206 从 offset 到末尾")
    void openEndedRange() throws Exception {
        ByteArrayOutputStream baos = mockOutputStream();
        when(request.getHeader("Range")).thenReturn("bytes=1024-");

        service.streamFile(tempFile.toString(), request, response);

        verify(response).setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
        assertEquals(8192 - 1024, baos.size());
    }

    @Test
    @DisplayName("Range bytes=-2048 → 206 最后2KB")
    void suffixRange() throws Exception {
        ByteArrayOutputStream baos = mockOutputStream();
        when(request.getHeader("Range")).thenReturn("bytes=-2048");

        service.streamFile(tempFile.toString(), request, response);

        verify(response).setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
        assertEquals(2048, baos.size());
    }


    @Test
    @DisplayName("streamSong 通过 ID 查找并流式传输")
    void streamSongById() throws Exception {
        ByteArrayOutputStream baos = mockOutputStream();
        when(musicMapper.findSongById(42L)).thenReturn(
                Song.builder().id("42").filePath(tempFile.toString()).build());

        service.streamSong(42L, request, response);

        assertEquals(8192, baos.size());
    }

    @Test
    @DisplayName("streamSong songId 不存在 → 404")
    void streamSongNotFound() throws Exception {
        when(musicMapper.findSongById(999L)).thenReturn(null);
        mockOutputStream();

        service.streamSong(999L, request, response);

        verify(response).sendError(HttpServletResponse.SC_NOT_FOUND,
                "歌曲不存在: id=999");
    }

    @Test
    @DisplayName("streamSong filePath 为 null → 404")
    void streamSongNullPath() throws Exception {
        when(musicMapper.findSongById(1L)).thenReturn(
                Song.builder().id("1").filePath(null).build());
        mockOutputStream();

        service.streamSong(1L, request, response);

        verify(response).sendError(HttpServletResponse.SC_NOT_FOUND,
                "歌曲不存在: id=1");
    }


    @Test
    @DisplayName("响应包含安全头")
    void securityHeaders() throws Exception {
        mockOutputStream();

        service.streamFile(tempFile.toString(), request, response);

        verify(response).setHeader("X-Content-Type-Options", "nosniff");
        verify(response).setHeader("Accept-Ranges", "bytes");
    }
}
