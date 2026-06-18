package com.gjl.music.playback.transcoding;
import com.gjl.music.playback.infra.transcoding.FileBasedTranscodingCache;

import com.gjl.music.playback.config.PlaybackProperties;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;


@DisplayName("FileBasedTranscodingCache 单元测试")
class FileBasedTranscodingCacheTest {

    @TempDir
    Path tempDir;

    private PlaybackProperties properties;
    private FileBasedTranscodingCache cache;

    @BeforeEach
    void setUp() {
        properties = new PlaybackProperties();
        properties.getCache().setTranscodingCacheDir(tempDir.toString());
        properties.getCache().setTranscodingCacheMaxSizeMB(10);
        cache = new FileBasedTranscodingCache(properties);
    }

    @AfterEach
    void tearDown() {
            }


    @Test
    @DisplayName("缓存未命中：执行 transcoder 并写入文件")
    void cacheMiss() throws Exception {
        byte[] data = "transcoded audio data".getBytes();
        Supplier<InputStream> transcoder = () -> new ByteArrayInputStream(data);

        Path cached = cache.getOrCompute("test-key-001", transcoder);

        assertNotNull(cached);
        assertTrue(Files.exists(cached));
        assertTrue(Files.size(cached) > 0);
        assertEquals(1, cache.getFileCount());
        assertTrue(cache.getCurrentSize() > 0);
    }

    @Test
    @DisplayName("缓存命中：不执行 transcoder，直接返回")
    void cacheHit() throws Exception {
        byte[] data = "precious cached data".getBytes();

                cache.getOrCompute("cache-hit-1", () -> new ByteArrayInputStream(data));
        long sizeAfterFirst = cache.getCurrentSize();

                int[] callCount = {0};
        Path cached = cache.getOrCompute("cache-hit-1", () -> {
            callCount[0]++;
            return new ByteArrayInputStream("should not be called".getBytes());
        });

        assertNotNull(cached);
        assertEquals(0, callCount[0]);
        assertEquals(sizeAfterFirst, cache.getCurrentSize());
        assertEquals(1, cache.getFileCount());
    }

    @Test
    @DisplayName("写入失败时清理残留文件")
    void writeFailure() {
        Supplier<InputStream> failingTranscoder = () -> {
            throw new RuntimeException("模拟转码失败");
        };

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> cache.getOrCompute("fail-key", failingTranscoder));
        assertTrue(ex.getMessage().contains("转码缓存写入失败"));
                assertEquals(0, cache.getFileCount());
    }


    @Test
    @DisplayName("超出容量时自动驱逐旧条目")
    void evictionOnOverflow() throws Exception {
                properties.getCache().setTranscodingCacheMaxSizeMB(1);
        cache = new FileBasedTranscodingCache(properties);

                for (int i = 0; i < 20; i++) {
            byte[] data = new byte[100 * 1024];
            final int idx = i;
            try {
                cache.getOrCompute("evict-key-" + idx,
                        () -> new ByteArrayInputStream(data));
            } catch (Exception e) {
                            }
        }

                assertTrue(cache.getCurrentSize() <= properties.getCache()
                .getTranscodingCacheMaxSizeMB() * 1024L * 1024L,
                "缓存大小超出上限");
    }


    @Test
    @DisplayName("手动驱逐到目标大小")
    void manualEviction() throws Exception {
                for (int i = 0; i < 5; i++) {
            byte[] data = new byte[50 * 1024];
            cache.getOrCompute("man-evict-" + i,
                    () -> new ByteArrayInputStream(data));
        }

        long before = cache.getCurrentSize();
        assertTrue(before > 0);

                cache.evictToTarget(1024);
        assertTrue(cache.getCurrentSize() <= 1024 || cache.getFileCount() == 0);
    }

    @Test
    @DisplayName("驱逐到大于当前大小时不操作")
    void evictionTargetLargerThanCurrent() throws Exception {
        cache.getOrCompute("key-1",
                () -> new ByteArrayInputStream(new byte[1024]));
        long before = cache.getCurrentSize();

        cache.evictToTarget(Long.MAX_VALUE);
        assertEquals(before, cache.getCurrentSize());
    }


    @Test
    @DisplayName("init 扫描已有缓存文件")
    void initScansExistingFiles() throws Exception {
                cache.getOrCompute("existing-1",
                () -> new ByteArrayInputStream(new byte[1024]));
        cache.getOrCompute("existing-2",
                () -> new ByteArrayInputStream(new byte[2048]));

        long size = cache.getCurrentSize();
        int count = cache.getFileCount();
        assertEquals(2, count);

                FileBasedTranscodingCache newCache = new FileBasedTranscodingCache(properties);
                newCache.init();

        assertEquals(count, newCache.getFileCount());
        assertEquals(size, newCache.getCurrentSize());
    }


    @Test
    @DisplayName("空缓存初始状态正确")
    void emptyState() throws Exception {
                FileBasedTranscodingCache fresh = new FileBasedTranscodingCache(properties);
        assertEquals(0, fresh.getFileCount());
        assertEquals(0, fresh.getCurrentSize());
    }


    @Test
    @DisplayName("短缓存键也能正常工作")
    void shortCacheKey() throws Exception {
        byte[] data = "data".getBytes();
        Path cached = cache.getOrCompute("ab", () -> new ByteArrayInputStream(data));
        assertTrue(Files.exists(cached));
    }

    @Test
    @DisplayName("长缓存键使用前2字符做子目录")
    void longCacheKey() throws Exception {
        byte[] data = "data".getBytes();
        Path cached = cache.getOrCompute("a1b2c3d4e5f6.123.192.mp3.0",
                () -> new ByteArrayInputStream(data));

                Path parent = cached.getParent();
        assertNotNull(parent);
        assertEquals("a1", parent.getFileName().toString());
    }
}
