package com.amoylabs.sfe4j.spring.boot.web;

import java.util.concurrent.Semaphore;

/**
 * Bounds the number of concurrent downloads.
 *
 * <p>No waiting queue is used: when every permit is taken the request is rejected with 429 instead of
 * occupying a Servlet container thread. A permit is released by the download stream when the response
 * body is closed or the transfer is aborted.</p>
 */
public class DownloadConcurrencyLimiter {

    private final Semaphore permits;

    public DownloadConcurrencyLimiter(int maxConcurrent) {
        this.permits = new Semaphore(Math.max(1, maxConcurrent), true);
    }

    public boolean tryAcquire() {
        return permits.tryAcquire();
    }

    public void release() {
        permits.release();
    }

    public int availablePermits() {
        return permits.availablePermits();
    }
}
