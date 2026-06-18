package com.gjl.music.pipeline.engine;

import com.gjl.music.pipeline.NodeContext;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;


public class PauseController {

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition stateChanged = lock.newCondition();

    private volatile boolean pausing;
    private volatile boolean paused;
    private volatile boolean cancelled;


    private volatile long pauseStartMs;

    private volatile long totalPausedMs;


    public void pause() {
        pausing = true;
        paused = true;
        pauseStartMs = System.currentTimeMillis();
    }


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


    public long getTotalPausedMs() { return totalPausedMs; }


    public long getPauseStartMs() { return pauseStartMs; }

    public boolean isPausing() { return pausing; }
    public boolean isPaused() { return paused; }
    public boolean isCancelled() { return cancelled; }


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
                        if (cancelled) {
                throw new NodeContext.CancelledException();
            }
        }
    }
}
