package com.gjl.music.playback.playlist;
import com.gjl.music.playback.infra.m3u.M3uParser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;


@DisplayName("M3uParser 单元测试")
class M3uParserTest {


    @Nested
    @DisplayName("M3U 解析")
    class Parse {

        @Test
        @DisplayName("标准 M3U8（含 #EXTINF）")
        void standardM3u8() {
            String content = """
                #EXTM3U
                #EXTINF:286,01. 夜曲
                /music/Jay/November's Chopin/01. 夜曲.flac
                #EXTINF:259,02. 蓝色风暴
                /music/Jay/November's Chopin/02. 蓝色风暴.flac
                """;

            List<M3uParser.M3uEntry> entries = M3uParser.parse(content);

            assertEquals(2, entries.size());
            assertEquals("/music/Jay/November's Chopin/01. 夜曲.flac", entries.get(0).path());
            assertEquals("01. 夜曲", entries.get(0).title());
            assertEquals(286, entries.get(0).duration());

            assertEquals("/music/Jay/November's Chopin/02. 蓝色风暴.flac", entries.get(1).path());
            assertEquals("02. 蓝色风暴", entries.get(1).title());
            assertEquals(259, entries.get(1).duration());
        }

        @Test
        @DisplayName("无 #EXTINF 的简单 M3U")
        void simpleM3uNoExtinf() {
            String content = """
                /music/song1.mp3
                /music/song2.flac
                C:\\music\\song3.wav
                """;

            List<M3uParser.M3uEntry> entries = M3uParser.parse(content);

            assertEquals(3, entries.size());
            assertEquals("/music/song1.mp3", entries.get(0).path());
            assertNull(entries.get(0).title());
            assertEquals(-1, entries.get(0).duration());
        }

        @Test
        @DisplayName("跳过空行和 #EXTM3U 头")
        void skipEmptyLinesAndHeader() {
            String content = """
                #EXTM3U

                #EXTINF:100,Title
                /music/track.mp3

                # comment line
                """;

            List<M3uParser.M3uEntry> entries = M3uParser.parse(content);
            assertEquals(1, entries.size());
            assertEquals("/music/track.mp3", entries.get(0).path());
        }

        @Test
        @DisplayName("#EXTINF 无标题")
        void extinfNoTitle() {
            String content = """
                #EXTINF:180,
                /music/track.mp3
                """;

            List<M3uParser.M3uEntry> entries = M3uParser.parse(content);
            assertEquals(1, entries.size());
            assertEquals(180, entries.get(0).duration());
            assertNull(entries.get(0).title());
        }

        @Test
        @DisplayName("#EXTINF 非法 duration → -1")
        void extinfInvalidDuration() {
            String content = """
                #EXTINF:abc,Title
                /music/track.mp3
                """;

            List<M3uParser.M3uEntry> entries = M3uParser.parse(content);
            assertEquals(1, entries.size());
            assertEquals(-1, entries.get(0).duration());
            assertEquals("Title", entries.get(0).title());
        }

        @Test
        @DisplayName("空内容 → 空列表")
        void emptyContent() {
            List<M3uParser.M3uEntry> entries = M3uParser.parse("");
            assertTrue(entries.isEmpty());
        }

        @Test
        @DisplayName("相对路径保留原样")
        void relativePaths() {
            String content = """
                track01.mp3
                ../artist/album/track02.flac
                """;

            List<M3uParser.M3uEntry> entries = M3uParser.parse(content);
            assertEquals(2, entries.size());
            assertEquals("track01.mp3", entries.get(0).path());
            assertEquals("../artist/album/track02.flac", entries.get(1).path());
        }
    }


    @Nested
    @DisplayName("M3U8 序列化")
    class Serialize {

        @Test
        @DisplayName("toM3u8 正确输出格式")
        void toM3u8() {
            List<M3uParser.M3uEntry> entries = List.of(
                    new M3uParser.M3uEntry("/music/01.flac", "夜曲", 286),
                    new M3uParser.M3uEntry("/music/02.flac", "蓝色风暴", 259)
            );

            String output = M3uParser.toM3u8(entries);

            assertTrue(output.startsWith("#EXTM3U\n"));
            assertTrue(output.contains("#EXTINF:286,夜曲\n"));
            assertTrue(output.contains("#EXTINF:259,蓝色风暴\n"));
            assertTrue(output.contains("/music/01.flac\n"));
            assertTrue(output.contains("/music/02.flac\n"));
        }

        @Test
        @DisplayName("无标题时不输出 #EXTINF")
        void noTitleSkipsExtinf() {
            List<M3uParser.M3uEntry> entries = List.of(
                    new M3uParser.M3uEntry("/music/track.mp3", null, 180)
            );

            String output = M3uParser.toM3u8(entries);

            assertTrue(output.startsWith("#EXTM3U\n"));
            assertFalse(output.contains("#EXTINF:180,"));
            assertEquals("#EXTM3U\n/music/track.mp3\n", output);
        }

        @Test
        @DisplayName("duration 为负时写为 0")
        void negativeDuration() {
            List<M3uParser.M3uEntry> entries = List.of(
                    new M3uParser.M3uEntry("/music/track.mp3", "Title", -1)
            );

            String output = M3uParser.toM3u8(entries);
            assertTrue(output.contains("#EXTINF:0,Title"));
        }

        @Test
        @DisplayName("空列表 → 仅 #EXTM3U 头")
        void emptyList() {
            String output = M3uParser.toM3u8(List.of());
            assertEquals("#EXTM3U\n", output);
        }
    }


    @Nested
    @DisplayName("M3uEntry record")
    class M3uEntryRecord {

        @Test
        @DisplayName("record 组件访问正确")
        void recordAccessors() {
            M3uParser.M3uEntry e = new M3uParser.M3uEntry("/path", "title", 100);
            assertEquals("/path", e.path());
            assertEquals("title", e.title());
            assertEquals(100, e.duration());
        }

        @Test
        @DisplayName("record equals 和 hashCode")
        void equalsAndHashCode() {
            var e1 = new M3uParser.M3uEntry("/a", "t", 1);
            var e2 = new M3uParser.M3uEntry("/a", "t", 1);
            assertEquals(e1, e2);
            assertEquals(e1.hashCode(), e2.hashCode());
        }
    }
}
