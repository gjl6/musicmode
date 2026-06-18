package com.gjl.music.playback.transcoding;
import com.gjl.music.playback.infra.transcoding.TranscodeDeciderImpl;
import com.gjl.music.playback.model.TranscodeDecision;

import com.gjl.music.model.Song;
import com.gjl.music.playback.config.PlaybackProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;


@DisplayName("TranscodeDeciderImpl 单元测试")
class TranscodeDeciderImplTest {

    private TranscodeDeciderImpl decider;

    @BeforeEach
    void setUp() {
        PlaybackProperties props = new PlaybackProperties();
        decider = new TranscodeDeciderImpl(props);
    }


    private Song buildSong(String format, int bitrate, int sampleRate, int channels) {
        return Song.builder()
                .id("1")
                .filePath("/music/test." + format)
                .fileName("test." + format)
                .fileFormat(format)
                .bitrate(bitrate)
                .sampleRate(sampleRate)
                .channels(channels)
                .build();
    }


    @Nested
    @DisplayName("直接播放（无需转码）")
    class DirectPlay {

        @Test
        @DisplayName("无格式请求 → 直接播放")
        void noFormatRequest() {
            Song song = buildSong("flac", 900, 44100, 2);
            TranscodeDecision d = decider.decide(song, null, 0);
            assertTrue(d.canDirectPlay());
        }

        @Test
        @DisplayName("reqFormat='raw' → 直接播放")
        void rawFormat() {
            Song song = buildSong("flac", 900, 44100, 2);
            TranscodeDecision d = decider.decide(song, "raw", 0);
            assertTrue(d.canDirectPlay());
        }

        @Test
        @DisplayName("空格式请求 → 直接播放")
        void emptyFormat() {
            Song song = buildSong("mp3", 320, 44100, 2);
            TranscodeDecision d = decider.decide(song, "", 0);
            assertTrue(d.canDirectPlay());
        }

        @Test
        @DisplayName("源格式 = 目标格式 且无比特率约束 → 直接播放")
        void sameFormatNoBitrateConstraint() {
            Song song = buildSong("mp3", 320, 44100, 2);
            TranscodeDecision d = decider.decide(song, "mp3", 0);
            assertTrue(d.canDirectPlay());
        }

        @Test
        @DisplayName("源格式 = 目标格式 且比特率不超标 → 直接播放")
        void sameFormatBitrateWithinLimit() {
            Song song = buildSong("mp3", 192, 44100, 2);
            TranscodeDecision d = decider.decide(song, "mp3", 320);
            assertTrue(d.canDirectPlay());
        }

        @Test
        @DisplayName("不支持的目标格式 → 直接播放")
        void unsupportedFormat() {
            Song song = buildSong("flac", 900, 44100, 2);
            TranscodeDecision d = decider.decide(song, "wma", 0);
            assertTrue(d.canDirectPlay());
        }
    }


    @Nested
    @DisplayName("需要转码")
    class NeedsTranscode {

        @Test
        @DisplayName("FLAC → MP3（有损转码）")
        void flacToMp3() {
            Song song = buildSong("flac", 900, 44100, 2);
            TranscodeDecision d = decider.decide(song, "mp3", 320);

            assertFalse(d.canDirectPlay());
            assertEquals("mp3", d.targetFormat());
            assertEquals(192, d.targetBitrate());
            assertEquals(44100, d.targetSampleRate());
            assertEquals("mp3 audio", d.commandName());
        }

        @Test
        @DisplayName("FLAC → OPUS")
        void flacToOpus() {
            Song song = buildSong("flac", 900, 44100, 2);
            TranscodeDecision d = decider.decide(song, "opus", 0);

            assertFalse(d.canDirectPlay());
            assertEquals("opus", d.targetFormat());
            assertEquals(128, d.targetBitrate());
            assertEquals(48000, d.targetSampleRate());
            assertEquals(0, d.targetChannels());
            assertEquals("opus audio", d.commandName());
        }

        @Test
        @DisplayName("FLAC → AAC")
        void flacToAac() {
            Song song = buildSong("flac", 900, 44100, 2);
            TranscodeDecision d = decider.decide(song, "aac", 0);

            assertFalse(d.canDirectPlay());
            assertEquals("aac", d.targetFormat());
            assertEquals(256, d.targetBitrate());
            assertEquals("aac audio", d.commandName());
        }

        @Test
        @DisplayName("FLAC → FLAC（格式相同直接播放）")
        void flacToFlac() {
            Song song = buildSong("flac", 900, 44100, 2);
                        TranscodeDecision d = decider.decide(song, "flac", 0);

            assertTrue(d.canDirectPlay());
        }

        @Test
        @DisplayName("MP3 320 → MP3 128（降比特率）")
        void mp3DownBitrate() {
            Song song = buildSong("mp3", 320, 44100, 2);
            TranscodeDecision d = decider.decide(song, "mp3", 128);

            assertFalse(d.canDirectPlay());
            assertEquals("mp3", d.targetFormat());
            assertEquals(128, d.targetBitrate());
        }

        @Test
        @DisplayName("MP3 → AAC 格式转换")
        void mp3ToAac() {
            Song song = buildSong("mp3", 320, 44100, 2);
            TranscodeDecision d = decider.decide(song, "aac", 0);

            assertFalse(d.canDirectPlay());
            assertEquals("aac", d.targetFormat());
        }
    }


    @Nested
    @DisplayName("拒绝转码（降级直接播放）")
    class RejectTranscode {

        @Test
        @DisplayName("有损→无损拒绝: MP3 → FLAC")
        void lossyToLossless() {
            Song song = buildSong("mp3", 320, 44100, 2);
            TranscodeDecision d = decider.decide(song, "flac", 0);

            assertTrue(d.canDirectPlay());
        }

        @Test
        @DisplayName("AAC → FLAC 也拒绝")
        void aacToFlac() {
            Song song = buildSong("aac", 256, 44100, 2);
            TranscodeDecision d = decider.decide(song, "flac", 0);

            assertTrue(d.canDirectPlay());
        }
    }


    @Nested
    @DisplayName("编解码器采样率/声道限制")
    class CodecLimits {

        @Test
        @DisplayName("MP3 采样率上限 48kHz")
        void mp3SampleRateLimit() {
            Song song = buildSong("flac", 900, 96000, 2);
            TranscodeDecision d = decider.decide(song, "mp3", 320);

            assertEquals(48000, d.targetSampleRate());
        }

        @Test
        @DisplayName("MP3 声道上限 2ch")
        void mp3ChannelLimit() {
            Song song = buildSong("flac", 900, 44100, 6);
            TranscodeDecision d = decider.decide(song, "mp3", 320);

            assertEquals(2, d.targetChannels());
        }

        @Test
        @DisplayName("AAC 采样率上限 96kHz")
        void aacSampleRateLimit() {
            Song song = buildSong("flac", 900, 192000, 2);
            TranscodeDecision d = decider.decide(song, "aac", 0);

            assertEquals(96000, d.targetSampleRate());
        }

        @Test
        @DisplayName("Opus 固定 48kHz")
        void opusFixed48kHz() {
            Song song = buildSong("flac", 900, 192000, 2);
            TranscodeDecision d = decider.decide(song, "opus", 0);

            assertEquals(48000, d.targetSampleRate());
        }

        @Test
        @DisplayName("采样率未知(0) → 不设限")
        void unknownSampleRate() {
            Song song = buildSong("flac", 900, 0, 0);
            TranscodeDecision d = decider.decide(song, "mp3", 320);

            assertEquals(0, d.targetSampleRate());
            assertEquals(0, d.targetChannels());
        }
    }


    @Nested
    @DisplayName("比特率计算")
    class BitrateLogic {

        @Test
        @DisplayName("maxBitRate 限制 defaultBitrate")
        void maxBitRateClampsDefault() {
            Song song = buildSong("flac", 900, 44100, 2);
                        TranscodeDecision d = decider.decide(song, "mp3", 128);

            assertEquals(128, d.targetBitrate());
        }

        @Test
        @DisplayName("maxBitRate=0 时用默认比特率")
        void zeroMaxBitRate() {
            Song song = buildSong("flac", 900, 44100, 2);
            TranscodeDecision d = decider.decide(song, "mp3", 0);

            assertEquals(192, d.targetBitrate());
        }

        @Test
        @DisplayName("flac 无损 + maxBitRate>0 → 降级为 192")
        void losslessWithBitrateLimit() {
            Song song = buildSong("flac", 900, 44100, 2);
            TranscodeDecision d = decider.decide(song, "flac", 128);

            assertEquals(192, d.targetBitrate());
        }
    }


    @Nested
    @DisplayName("边界情况")
    class EdgeCases {

        @Test
        @DisplayName("song.fileFormat 为 null → 直接播放")
        void nullFileFormat() {
            Song song = Song.builder()
                    .id("1")
                    .filePath("/music/test.mp3")
                    .fileName("test.mp3")
                    .fileFormat(null)
                    .build();
            TranscodeDecision d = decider.decide(song, null, 0);
                        assertTrue(d.canDirectPlay());
        }

        @Test
        @DisplayName("带 ' audio' 后缀的格式名规范化")
        void formatWithAudioSuffix() {
            Song song = buildSong("flac", 900, 44100, 2);
            TranscodeDecision d = decider.decide(song, "mp3 audio", 0);
                        assertFalse(d.canDirectPlay());
            assertEquals("mp3", d.targetFormat());
        }

        @Test
        @DisplayName("m4a 规范化为 aac")
        void m4aCanonicalToAac() {
            Song song = buildSong("m4a", 256, 44100, 2);
                        TranscodeDecision d = decider.decide(song, "aac", 0);
            assertTrue(d.canDirectPlay());
        }

        @Test
        @DisplayName("源文件无比特率信息")
        void nullBitrate() {
            Song song = Song.builder()
                    .id("1")
                    .filePath("/music/test.flac")
                    .fileName("test.flac")
                    .fileFormat("flac")
                    .bitrate(null)
                    .sampleRate(44100)
                    .channels(2)
                    .build();
                        TranscodeDecision d = decider.decide(song, "mp3", 0);
            assertFalse(d.canDirectPlay());
            assertEquals(192, d.targetBitrate());
        }
    }
}
