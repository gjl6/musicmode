package com.gjl.music.playback.infra.transcoding;

import com.gjl.music.playback.config.PlaybackProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;


@Slf4j
@Service
public class TranscodeLimiter {

    private final Semaphore globalSemaphore;
    private final int maxPerUser;
    private final ConcurrentMap<String, AtomicInteger> perUserCounts = new ConcurrentHashMap<>();

    public TranscodeLimiter(PlaybackProperties properties) {
        PlaybackProperties.Transcoding tc = properties.getTranscoding();
        this.globalSemaphore = tc.getMaxConcurrent() > 0
                ? new Semaphore(tc.getMaxConcurrent(), true)
                : null;
        this.maxPerUser = tc.getMaxConcurrentPerUser();
    }


    public boolean tryAcquire(String username) {
                if (maxPerUser > 0) {
            AtomicInteger userCount = perUserCounts.computeIfAbsent(username,
                    k -> new AtomicInteger(0));
            int current = userCount.incrementAndGet();
            if (current > maxPerUser) {
                userCount.decrementAndGet();
                log.debug("转码限流(per-user): user={}, current={}, max={}", username, current - 1, maxPerUser);
                return false;
            }
        }

                if (globalSemaphore != null) {
            if (!globalSemaphore.tryAcquire()) {
                                if (maxPerUser > 0) {
                    perUserCounts.get(username).decrementAndGet();
                }
                log.debug("转码限流(global): 已达上限");
                return false;
            }
        }

        return true;
    }


    public void release(String username) {
        if (globalSemaphore != null) {
            globalSemaphore.release();
        }
        if (maxPerUser > 0) {
            AtomicInteger userCount = perUserCounts.get(username);
            if (userCount != null) {
                userCount.decrementAndGet();
            }
        }
    }
}
