package com.gjl.music.playback.transcoding;
import com.gjl.music.playback.model.TranscodeDecision;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;


@DisplayName("TranscodeDecision 单元测试")
class TranscodeDecisionTest {

    @Test
    @DisplayName("directPlay 工厂方法创建正确 record")
    void directPlay() {
        TranscodeDecision d = TranscodeDecision.directPlay("mp3", "audio/mpeg");
        assertTrue(d.canDirectPlay());
        assertEquals("mp3", d.targetFormat());
        assertEquals(0, d.targetBitrate());
        assertEquals(0, d.targetSampleRate());
        assertEquals(0, d.targetChannels());
        assertEquals("audio/mpeg", d.targetMimeType());
        assertNull(d.commandName());
    }

    @Test
    @DisplayName("transcode 工厂方法创建正确 record")
    void transcode() {
        TranscodeDecision d = TranscodeDecision.transcode(
                "opus", 128, 48000, 2, "audio/ogg", "opus audio");
        assertFalse(d.canDirectPlay());
        assertEquals("opus", d.targetFormat());
        assertEquals(128, d.targetBitrate());
        assertEquals(48000, d.targetSampleRate());
        assertEquals(2, d.targetChannels());
        assertEquals("audio/ogg", d.targetMimeType());
        assertEquals("opus audio", d.commandName());
    }

    @Test
    @DisplayName("无损转码 targetBitrate=0 正确")
    void losslessTranscode() {
        TranscodeDecision d = TranscodeDecision.transcode(
                "flac", 0, 0, 0, "audio/flac", "flac audio");
        assertFalse(d.canDirectPlay());
        assertEquals("flac", d.targetFormat());
        assertEquals(0, d.targetBitrate());
    }

    @Test
    @DisplayName("record equals 和 hashCode 正确")
    void equalsAndHashCode() {
        TranscodeDecision d1 = TranscodeDecision.directPlay("mp3", "audio/mpeg");
        TranscodeDecision d2 = TranscodeDecision.directPlay("mp3", "audio/mpeg");
        assertEquals(d1, d2);
        assertEquals(d1.hashCode(), d2.hashCode());
    }

    @Test
    @DisplayName("different records are not equal")
    void notEqual() {
        TranscodeDecision d1 = TranscodeDecision.directPlay("mp3", "audio/mpeg");
        TranscodeDecision d2 = TranscodeDecision.transcode("mp3", 192, 44100, 2, "audio/mpeg", "mp3 audio");
        assertNotEquals(d1, d2);
    }
}
