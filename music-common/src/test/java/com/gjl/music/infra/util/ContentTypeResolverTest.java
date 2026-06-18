package com.gjl.music.infra.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;


@DisplayName("ContentTypeResolver 单元测试")
class ContentTypeResolverTest {

    @ParameterizedTest(name = "[{index}] {0} → {1}")
    @CsvSource(delimiter = '→', textBlock = """
        song.mp3   → audio/mpeg
        track.flac → audio/flac
        audio.wav  → audio/wav
        song.mp4   → audio/mp4
        song.m4a   → audio/mp4
        song.aac   → audio/mp4
        song.ogg   → audio/ogg
        song.opus  → audio/ogg
        song.wma   → audio/x-ms-wma
        song.aiff  → audio/aiff
        song.aif   → audio/aiff
        song.wv    → audio/x-wavpack
        song.ape   → audio/x-ape
        """)
    @DisplayName("文件扩展名 → MIME Type")
    void knownExtensions(String fileName, String expectedMime) {
        assertEquals(expectedMime, ContentTypeResolver.resolve(fileName));
    }

    @Test
    @DisplayName("未知扩展名 → application/octet-stream")
    void unknownExtension() {
        assertEquals("application/octet-stream",
                ContentTypeResolver.resolve("file.xyz"));
    }

    @Test
    @DisplayName("无扩展名 → application/octet-stream")
    void noExtension() {
        assertEquals("application/octet-stream",
                ContentTypeResolver.resolve("README"));
    }

    @Test
    @DisplayName("null 输入 → application/octet-stream")
    void nullInput() {
        assertEquals("application/octet-stream",
                ContentTypeResolver.resolve(null));
    }

    @Test
    @DisplayName("大小写不敏感")
    void caseInsensitive() {
        assertEquals("audio/mpeg", ContentTypeResolver.resolve("SONG.MP3"));
        assertEquals("audio/flac", ContentTypeResolver.resolve("Track.FLAC"));
        assertEquals("audio/ogg", ContentTypeResolver.resolve("Music.Ogg"));
    }

    @Test
    @DisplayName("带路径的文件名")
    void pathWithFileName() {
        assertEquals("audio/mpeg",
                ContentTypeResolver.resolve("/music/artist/album/song.mp3"));
        assertEquals("audio/flac",
                ContentTypeResolver.resolve("C:\\music\\track.flac"));
    }
}
