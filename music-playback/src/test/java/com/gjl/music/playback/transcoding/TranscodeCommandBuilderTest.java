package com.gjl.music.playback.transcoding;
import com.gjl.music.playback.infra.transcoding.TranscodeCommandBuilder;
import com.gjl.music.playback.model.TranscodeDecision;

import com.gjl.music.config.ConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
@DisplayName("TranscodeCommandBuilder 单元测试")
class TranscodeCommandBuilderTest {

    @Mock
    private ConfigService configService;

    private TranscodeCommandBuilder builder;

    @BeforeEach
    void setUp() {
        when(configService.getString("music.ffmpeg.path", "ffmpeg")).thenReturn("ffmpeg");
        builder = new TranscodeCommandBuilder(configService);
    }


    @Nested
    @DisplayName("MP3 转码命令")
    class Mp3Command {

        @Test
        @DisplayName("基本 MP3 转码命令")
        void basicMp3Command() {
            TranscodeDecision d = TranscodeDecision.transcode(
                    "mp3", 192, 44100, 2, "audio/mpeg", "mp3 audio");
            Path source = Path.of("/music/test.flac");

            List<String> cmd = builder.build(source, d, 0);

            assertTrue(cmd.get(0).contains("ffmpeg"));
            assertTrue(cmd.contains("-i"));
            assertTrue(cmd.contains(source.toString()));
            assertTrue(cmd.contains("-b:a"));
            assertTrue(cmd.contains("192k"));
            assertTrue(cmd.contains("-ar"));
            assertTrue(cmd.contains("44100"));
            assertTrue(cmd.contains("-ac"));
            assertTrue(cmd.contains("2"));
            assertTrue(cmd.contains("pipe:1"));
        }

        @Test
        @DisplayName("MP3 不设采样率/声道")
        void mp3NoSampleRateOrChannels() {
            TranscodeDecision d = TranscodeDecision.transcode(
                    "mp3", 192, 0, 0, "audio/mpeg", "mp3 audio");
            Path source = Path.of("/music/test.flac");

            List<String> cmd = builder.build(source, d, 0);

                        assertFalse(cmd.contains("-ar"));
            assertFalse(cmd.contains("-ac"));
                        assertTrue(cmd.contains("-b:a"));
            assertTrue(cmd.contains("192k"));
        }

        @Test
        @DisplayName("MP3 带时间偏移")
        void mp3WithTimeOffset() {
            TranscodeDecision d = TranscodeDecision.transcode(
                    "mp3", 192, 44100, 2, "audio/mpeg", "mp3 audio");
            Path source = Path.of("/music/test.flac");

            List<String> cmd = builder.build(source, d, 60);

            assertTrue(cmd.contains("-ss"));
            assertTrue(cmd.contains("60"));
                        int ssIdx = cmd.indexOf("-ss");
            int iIdx = cmd.indexOf("-i");
            assertTrue(ssIdx < iIdx);
        }
    }


    @Nested
    @DisplayName("其他格式转码命令")
    class OtherFormats {

        @Test
        @DisplayName("OPUS 转码命令")
        void opusCommand() {
            TranscodeDecision d = TranscodeDecision.transcode(
                    "opus", 128, 48000, 0, "audio/ogg", "opus audio");
            Path source = Path.of("/music/test.flac");

            List<String> cmd = builder.build(source, d, 0);

            assertTrue(cmd.contains("-b:a"));
            assertTrue(cmd.contains("128k"));
            assertTrue(cmd.contains("-ar"));
            assertTrue(cmd.contains("48000"));
            assertFalse(cmd.contains("-ac"));
        }

        @Test
        @DisplayName("AAC 转码命令")
        void aacCommand() {
            TranscodeDecision d = TranscodeDecision.transcode(
                    "aac", 256, 44100, 2, "audio/mp4", "aac audio");
            Path source = Path.of("/music/test.flac");

            List<String> cmd = builder.build(source, d, 0);

            assertTrue(cmd.contains("-b:a"));
            assertTrue(cmd.contains("256k"));
            assertTrue(cmd.contains("pipe:1"));
        }

        @Test
        @DisplayName("FLAC 转码命令（无损，无比特率参数）")
        void flacCommand() {
            TranscodeDecision d = TranscodeDecision.transcode(
                    "flac", 0, 0, 0, "audio/flac", "flac audio");
            Path source = Path.of("/music/test.mp3");

            List<String> cmd = builder.build(source, d, 0);

            assertFalse(cmd.contains("-b:a"));
            assertFalse(cmd.contains("-ar"));
            assertFalse(cmd.contains("-ac"));
            assertTrue(cmd.contains("pipe:1"));
        }
    }


    @Nested
    @DisplayName("命令结构验证")
    class CommandStructure {

        @Test
        @DisplayName("输出总是 pipe:1（stdout）")
        void alwaysPipeToStdout() {
            TranscodeDecision d = TranscodeDecision.transcode(
                    "mp3", 192, 0, 0, "audio/mpeg", "mp3 audio");

            List<String> cmd = builder.build(Path.of("/tmp/test.flac"), d, 0);
            assertEquals("pipe:1", cmd.get(cmd.size() - 1));
        }

        @Test
        @DisplayName("包含 -map 0:a:0（仅音频流）")
        void containsAudioMap() {
            TranscodeDecision d = TranscodeDecision.transcode(
                    "mp3", 192, 0, 0, "audio/mpeg", "mp3 audio");

            List<String> cmd = builder.build(Path.of("/tmp/test.flac"), d, 0);
            assertTrue(cmd.contains("-map"));
            assertTrue(cmd.contains("0:a:0"));
        }

        @Test
        @DisplayName("自定义 ffmpeg 路径")
        void customFfmpegPath() {
            when(configService.getString("music.ffmpeg.path", "ffmpeg"))
                    .thenReturn("/usr/local/bin/ffmpeg");
            TranscodeCommandBuilder customBuilder = new TranscodeCommandBuilder(configService);

            TranscodeDecision d = TranscodeDecision.transcode(
                    "mp3", 192, 0, 0, "audio/mpeg", "mp3 audio");

            List<String> cmd = customBuilder.build(Path.of("/tmp/test.flac"), d, 0);
            assertEquals("/usr/local/bin/ffmpeg", cmd.get(0));
        }
    }
}
