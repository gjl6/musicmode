package com.gjl.music.playback.infra.transcoding;

import com.gjl.music.playback.config.PlaybackProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 双层并发限流器。
 *
 * <p>防止过多并发转码耗尽 CPU：
 * <ul>
 *   <li>全局 Semaphore：限制总并发转码数</li>
 *   <li>每用户 AtomicInteger：限制单用户并发数</li>
 *   <li>先检查 per-user，再检查 global</li>
 *   <li>非阻塞：超出限制立即返回 false（调用方返回 HTTP 429）</li>
 * </ul>
 */
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

    /**
     * 尝试获取转码许可。
     *
     * @param username 用户名
     * @return true 表示获取成功，false 表示超出限制
     */
    public boolean tryAcquire(String username) {
        // 检查每用户限制
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

        // 检查全局限制
        if (globalSemaphore != null) {
            if (!globalSemaphore.tryAcquire()) {
                // 回滚 per-user 计数
                if (maxPerUser > 0) {
                    perUserCounts.get(username).decrementAndGet();
                }
                log.debug("转码限流(global): 已达上限");
                return false;
            }
        }

        return true;
    }

    /**
     * 释放转码许可。
     *
     * @param username 用户名
     */
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
