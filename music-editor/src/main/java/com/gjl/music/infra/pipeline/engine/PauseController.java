package com.gjl.music.infra.pipeline.engine;

import com.gjl.music.infra.pipeline.NodeContext;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 暂停/恢复/取消协调器 —— 所有 worker 线程共享同一实例。
 *
 * <p>使用 {@link ReentrantLock} + {@link Condition} 替代 {@code synchronized}，
 * 确保虚拟线程阻塞时能正确 unmount carrier thread。
 *
 * <h3>状态转换</h3>
 * <pre>
 * RUNNING → (pause) → PAUSING → PAUSED → (resume) → RUNNING
 *                                            → (cancel) → CANCELLED
 * RUNNING → (cancel) → CANCELLED
 * </pre>
 *
 * <p>worker 线程在处理循环中周期性调用 {@link #checkPause()}：
 * <ul>
 *   <li>若 pausing 或 paused，线程阻塞等待直到 resume 或 cancel</li>
 *   <li>若 cancelled，抛出 NodeContext.CancelledException</li>
 * </ul>
 */
public class PauseController {

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition stateChanged = lock.newCondition();

    private volatile boolean pausing;
    private volatile boolean paused;
    private volatile boolean cancelled;

    /** 本次暂停开始时间戳；0 表示未暂停 */
    private volatile long pauseStartMs;
    /** 累计暂停总时长（毫秒） */
    private volatile long totalPausedMs;

    /**
     * 请求暂停 —— 先设 pausing 标志让 worker 尽快感知，再设 paused。
     */
    public void pause() {
        pausing = true;
        paused = true;
        pauseStartMs = System.currentTimeMillis();
    }

    /**
     * 恢复执行 —— 清除暂停/取消标志，唤醒所有等待线程。
     */
    public void resume() {
        if ((paused || pausing) && pauseStartMs > 0) {
            totalPausedMs += System.currentTimeMillis() - pauseStartMs;
            pauseStartMs = 0;
        }
        pausing = false;
        paused = false;
        cancelled = false;
        lock.lock();
        try {
            stateChanged.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 取消管道 —— 唤醒等待线程，它们会在 checkPause() 中检测到 cancelled 并抛异常。
     */
    public void cancel() {
        if ((paused || pausing) && pauseStartMs > 0) {
            totalPausedMs += System.currentTimeMillis() - pauseStartMs;
            pauseStartMs = 0;
        }
        cancelled = true;
        pausing = false;
        paused = false;
        lock.lock();
        try {
            stateChanged.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /** 获取累计暂停总时长（毫秒），用于计算有效运行时间 */
    public long getTotalPausedMs() { return totalPausedMs; }

    /** 获取当前暂停开始时间戳；0 表示当前未暂停 */
    public long getPauseStartMs() { return pauseStartMs; }

    public boolean isPausing() { return pausing; }
    public boolean isPaused() { return paused; }
    public boolean isCancelled() { return cancelled; }

    /**
     * worker 线程在处理循环中周期性调用。
     * 若暂停则阻塞等待，若取消则抛异常。
     *
     * <p>使用 {@link ReentrantLock} + {@link Condition} 而非 {@code synchronized}，
     * 确保虚拟线程阻塞时能正确 unmount carrier thread（虚拟线程在
     * {@code synchronized} 块内阻塞不会 unmount）。
     */
    public void checkPause() throws NodeContext.CancelledException {
        if (cancelled) {
            throw new NodeContext.CancelledException();
        }
        if (pausing || paused) {
            lock.lock();
            try {
                while ((pausing || paused) && !cancelled) {
                    try {
                        stateChanged.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new NodeContext.CancelledException();
                    }
                }
            } finally {
                lock.unlock();
            }
            // 等待后再次检查是否被取消
            if (cancelled) {
                throw new NodeContext.CancelledException();
            }
        }
    }
}
