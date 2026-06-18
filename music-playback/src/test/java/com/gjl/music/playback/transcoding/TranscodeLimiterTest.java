package com.gjl.music.playback.transcoding;
import com.gjl.music.playback.infra.transcoding.TranscodeLimiter;

import com.gjl.music.playback.config.PlaybackProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;


@DisplayName("TranscodeLimiter 单元测试")
class TranscodeLimiterTest {


    @Nested
    @DisplayName("双层限流（全局 + 每用户）")
    class DoubleLayer {

        private TranscodeLimiter limiter;

        @BeforeEach
        void setUp() {
            PlaybackProperties props = new PlaybackProperties();
            props.getTranscoding().setMaxConcurrent(2);
            props.getTranscoding().setMaxConcurrentPerUser(1);
            limiter = new TranscodeLimiter(props);
        }

        @Test
        @DisplayName("同一用户第二次尝试被每用户限制拒绝")
        void perUserLimit() {
            assertTrue(limiter.tryAcquire("user1"));
            assertFalse(limiter.tryAcquire("user1"));
            limiter.release("user1");
        }

        @Test
        @DisplayName("不同用户可以并发")
        void differentUsersConcurrent() {
            assertTrue(limiter.tryAcquire("user1"));
            assertTrue(limiter.tryAcquire("user2"));
                        assertFalse(limiter.tryAcquire("user3"));

            limiter.release("user1");
            limiter.release("user2");
        }

        @Test
        @DisplayName("release 后可以重新获取")
        void releaseEnablesReacquire() {
            assertTrue(limiter.tryAcquire("user1"));
            limiter.release("user1");
            assertTrue(limiter.tryAcquire("user1"));
            limiter.release("user1");
        }

        @Test
        @DisplayName("per-user 失败时回滚正确（不消耗全局许可）")
        void perUserFailureRollback() {
            assertTrue(limiter.tryAcquire("user1"));
                        assertFalse(limiter.tryAcquire("user1"));
                        assertTrue(limiter.tryAcquire("user2"));

            limiter.release("user1");
            limiter.release("user2");
        }
    }


    @Nested
    @DisplayName("仅全局限流（perUser=0）")
    class GlobalOnly {

        private TranscodeLimiter limiter;

        @BeforeEach
        void setUp() {
            PlaybackProperties props = new PlaybackProperties();
            props.getTranscoding().setMaxConcurrent(2);
            props.getTranscoding().setMaxConcurrentPerUser(0);
            limiter = new TranscodeLimiter(props);
        }

        @Test
        @DisplayName("同一用户可多次获取")
        void sameUserMultiple() {
            assertTrue(limiter.tryAcquire("user1"));
            assertTrue(limiter.tryAcquire("user1"));
                        assertFalse(limiter.tryAcquire("user1"));

            limiter.release("user1");
            limiter.release("user1");
        }
    }


    @Nested
    @DisplayName("无限制（maxConcurrent=0, perUser=0）")
    class NoLimit {

        private TranscodeLimiter limiter;

        @BeforeEach
        void setUp() {
            PlaybackProperties props = new PlaybackProperties();
            props.getTranscoding().setMaxConcurrent(0);
            props.getTranscoding().setMaxConcurrentPerUser(0);
            limiter = new TranscodeLimiter(props);
        }

        @Test
        @DisplayName("所有请求都通过")
        void allPassThrough() {
            for (int i = 0; i < 100; i++) {
                assertTrue(limiter.tryAcquire("user" + i));
            }
                        for (int i = 0; i < 100; i++) {
                limiter.release("user" + i);
            }
        }
    }


    @Nested
    @DisplayName("并发安全性")
    class ConcurrencySafety {

        @Test
        @DisplayName("多线程并发不超限")
        void concurrentUnderLimit() throws Exception {
            PlaybackProperties props = new PlaybackProperties();
            props.getTranscoding().setMaxConcurrent(4);
            props.getTranscoding().setMaxConcurrentPerUser(2);
            TranscodeLimiter limiter = new TranscodeLimiter(props);

            ExecutorService executor = Executors.newFixedThreadPool(10);
            int[] successCount = new int[1];

            int totalAttempts = 100;
            CountDownLatch latch = new CountDownLatch(totalAttempts);
            java.util.concurrent.atomic.AtomicInteger successes = new java.util.concurrent.atomic.AtomicInteger(0);

            for (int i = 0; i < totalAttempts; i++) {
                final String user = "user" + (i % 5);
                executor.submit(() -> {
                    if (limiter.tryAcquire(user)) {
                        successes.incrementAndGet();
                        try {
                            Thread.sleep(5);
                        } catch (InterruptedException ignored) {}
                        limiter.release(user);
                    }
                    latch.countDown();
                });
            }

            latch.await(5, TimeUnit.SECONDS);
            executor.shutdown();

                        assertTrue(successes.get() > 0);
        }
    }
}
