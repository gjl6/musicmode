package com.gjl.music.playback.streaming;
import com.gjl.music.playback.infra.streaming.RangeParser;

import com.gjl.music.playback.model.Range;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;


@DisplayName("RangeParser 单元测试")
class RangeParserTest {


    @Nested
    @DisplayName("空输入处理")
    class NullAndEmpty {

        @Test
        @DisplayName("null 返回 null")
        void nullInput() {
            assertNull(RangeParser.parse(null, 1000));
        }

        @Test
        @DisplayName("空字符串返回 null")
        void blankInput() {
            assertNull(RangeParser.parse("   ", 1000));
        }

        @Test
        @DisplayName("非法格式返回 null")
        void malformedInput() {
            assertNull(RangeParser.parse("garbage", 1000));
        }
    }


    @Nested
    @DisplayName("标准范围 bytes=start-end")
    class StandardRange {

        @Test
        @DisplayName("bytes=0-1023（前1KB）")
        void first1KB() {
            Range r = RangeParser.parse("bytes=0-1023", 8192);
            assertNotNull(r);
            assertEquals(0, r.getStart());
            assertEquals(1023, r.getEnd());
            assertEquals(1024, r.getLength());
            assertEquals("bytes 0-1023/8192", r.toContentRangeHeader(8192));
        }

        @Test
        @DisplayName("bytes=1024-2047（中间范围）")
        void middleRange() {
            Range r = RangeParser.parse("bytes=1024-2047", 8192);
            assertNotNull(r);
            assertEquals(1024, r.getStart());
            assertEquals(2047, r.getEnd());
            assertEquals(1024, r.getLength());
        }

        @Test
        @DisplayName("end 超出文件大小时截断到 fileSize-1")
        void endClampedToFileSize() {
            Range r = RangeParser.parse("bytes=0-99999", 8192);
            assertNotNull(r);
            assertEquals(0, r.getStart());
            assertEquals(8191, r.getEnd());
        }

        @Test
        @DisplayName("start > end → unsatisfiable")
        void startGreaterThanEnd() {
            Range r = RangeParser.parse("bytes=100-50", 8192);
            assertNotNull(r);
            assertTrue(r.isUnsatisfiable());
        }

        @Test
        @DisplayName("start >= fileSize → unsatisfiable")
        void startExceedsFileSize() {
            Range r = RangeParser.parse("bytes=8192-9000", 8192);
            assertNotNull(r);
            assertTrue(r.isUnsatisfiable());
        }
    }


    @Nested
    @DisplayName("开放结束 bytes=start-")
    class OpenEndedRange {

        @Test
        @DisplayName("bytes=1024-（从 offset 到末尾）")
        void fromOffsetToEnd() {
            Range r = RangeParser.parse("bytes=1024-", 8192);
            assertNotNull(r);
            assertEquals(1024, r.getStart());
            assertEquals(8191, r.getEnd());
            assertEquals(8192 - 1024, r.getLength());
        }

        @Test
        @DisplayName("bytes=0-（整个文件）")
        void entireFile() {
            Range r = RangeParser.parse("bytes=0-", 8192);
            assertNotNull(r);
            assertEquals(0, r.getStart());
            assertEquals(8191, r.getEnd());
            assertEquals(8192, r.getLength());
        }

        @Test
        @DisplayName("start >= fileSize → unsatisfiable")
        void startExceedsFileSize_openEnded() {
            Range r = RangeParser.parse("bytes=99999-", 8192);
            assertNotNull(r);
            assertTrue(r.isUnsatisfiable());
        }
    }


    @Nested
    @DisplayName("后缀请求 bytes=-suffix")
    class SuffixRange {

        @Test
        @DisplayName("bytes=-2048（最后2KB）")
        void last2KB() {
            Range r = RangeParser.parse("bytes=-2048", 8192);
            assertNotNull(r);
            assertEquals(8192 - 2048, r.getStart());
            assertEquals(8191, r.getEnd());
            assertEquals(2048, r.getLength());
        }

        @Test
        @DisplayName("bytes=-99999（suffix > fileSize，给整个文件）")
        void suffixExceedsFileSize() {
            Range r = RangeParser.parse("bytes=-99999", 8192);
            assertNotNull(r);
            assertEquals(0, r.getStart());
            assertEquals(8191, r.getEnd());
            assertEquals(8192, r.getLength());
        }

        @Test
        @DisplayName("bytes=-0 → unsatisfiable")
        void zeroSuffix() {
            Range r = RangeParser.parse("bytes=-0", 8192);
            assertNotNull(r);
            assertTrue(r.isUnsatisfiable());
        }
    }


    @Nested
    @DisplayName("多范围与边界情况")
    class MultiRangeAndEdgeCases {

        @Test
        @DisplayName("多范围请求 bytes=0-1023, 2048-4095 返回首个范围")
        void multiRangeReturnsFirst() {
                        Range r = RangeParser.parse("bytes=0-1023, 2048-4095", 8192);
            assertNotNull(r);
            assertEquals(0, r.getStart());
            assertEquals(1023, r.getEnd());
        }

        @Test
        @DisplayName("带空格的标准范围")
        void rangeWithSpaces() {
            Range r = RangeParser.parse("bytes=0-1023   ", 8192);
            assertNotNull(r);
            assertEquals(0, r.getStart());
            assertEquals(1023, r.getEnd());
        }

        @Test
        @DisplayName("非法数字返回 null")
        void nonNumericRange() {
            assertNull(RangeParser.parse("bytes=abc-def", 8192));
        }
    }


    @Nested
    @DisplayName("Range 工具方法")
    class RangeMethods {

        @Test
        @DisplayName("unsatisfiable getEnd 返回 -1")
        void unsatisfiableEnd() {
            Range r = Range.unsatisfiable();
            assertEquals(-1, r.getEnd());
            assertEquals(0, r.getLength());
            assertEquals("bytes */1000", r.toContentRangeHeader(1000));
        }

        @Test
        @DisplayName("toString 包含范围信息")
        void toStringOutput() {
            Range r = new Range(0, 1023, false);
            String s = r.toString();
            assertTrue(s.contains("0-1023"));
            assertTrue(s.contains("1024"));
        }

        @Test
        @DisplayName("unsatisfiable toString")
        void unsatisfiableToString() {
            Range r = Range.unsatisfiable();
            assertTrue(r.toString().contains("unsatisfiable"));
        }
    }
}
