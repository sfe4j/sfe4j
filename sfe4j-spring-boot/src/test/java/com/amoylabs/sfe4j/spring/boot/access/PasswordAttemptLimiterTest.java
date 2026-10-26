package com.amoylabs.sfe4j.spring.boot.access;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordAttemptLimiterTest {

    @Test
    void attemptsAreLimitedInsideTheWindow() {
        AtomicLong now = new AtomicLong(0L);
        PasswordAttemptLimiter limiter = new PasswordAttemptLimiter(3, now::get);

        assertTrue(limiter.tryAcquire("session|127.0.0.1"));
        assertTrue(limiter.tryAcquire("session|127.0.0.1"));
        assertTrue(limiter.tryAcquire("session|127.0.0.1"));
        assertFalse(limiter.tryAcquire("session|127.0.0.1"));
    }

    @Test
    void windowSlidesSoTheClientRecoversAfterAMinute() {
        AtomicLong now = new AtomicLong(0L);
        PasswordAttemptLimiter limiter = new PasswordAttemptLimiter(1, now::get);

        assertTrue(limiter.tryAcquire("key"));
        assertFalse(limiter.tryAcquire("key"));

        now.addAndGet(60_001L);
        assertTrue(limiter.tryAcquire("key"));
    }

    @Test
    void differentSessionsAreLimitedIndependently() {
        AtomicLong now = new AtomicLong(0L);
        PasswordAttemptLimiter limiter = new PasswordAttemptLimiter(1, now::get);

        assertTrue(limiter.tryAcquire("first|127.0.0.1"));
        assertFalse(limiter.tryAcquire("first|127.0.0.1"));
        assertTrue(limiter.tryAcquire("second|127.0.0.1"));
    }

    @Test
    void resetAfterSuccessRestoresTheBudget() {
        AtomicLong now = new AtomicLong(0L);
        PasswordAttemptLimiter limiter = new PasswordAttemptLimiter(1, now::get);

        assertTrue(limiter.tryAcquire("key"));
        limiter.reset("key");

        assertTrue(limiter.tryAcquire("key"));
    }

    @Test
    void sweepDropsExpiredWindows() {
        AtomicLong now = new AtomicLong(0L);
        PasswordAttemptLimiter limiter = new PasswordAttemptLimiter(2, now::get);

        assertTrue(limiter.tryAcquire("stale"));
        assertTrue(limiter.tryAcquire("fresh"));
        now.addAndGet(60_001L);
        assertTrue(limiter.tryAcquire("fresh"));

        limiter.sweep();

        assertTrue(limiter.trackedKeys() <= 1, "expired windows must not stay resident");
    }
}
