package com.amoylabs.sfe4j.spring.boot.access;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * In-memory sliding window limiter for password attempts.
 *
 * <p>The key combines the session id and the client address. {@code X-Forwarded-For} is intentionally
 * ignored: a client controlled header must not be able to reset the limit. The cache is bounded and
 * expired windows are evicted periodically, so creating new sessions cannot grow memory without
 * bound.</p>
 *
 * <p>The state is local to one instance. A deployment with several instances applies the limit per
 * instance; a shared backend is left to the later scaling stage.</p>
 */
public class PasswordAttemptLimiter implements AutoCloseable {

    private static final long WINDOW_MILLIS = 60_000L;

    private static final int MAX_ENTRIES = 4096;

    private static final long SWEEP_INTERVAL_SECONDS = 60L;

    private final int maxAttempts;

    private final LongSupplier clock;

    private final Map<String, Deque<Long>> attempts = new ConcurrentHashMap<>();

    private final ScheduledExecutorService sweeper;

    public PasswordAttemptLimiter(int maxAttempts) {
        this(maxAttempts, System::currentTimeMillis, true);
    }

    /**
     * Test friendly constructor without the background sweeper.
     */
    PasswordAttemptLimiter(int maxAttempts, LongSupplier clock) {
        this(maxAttempts, clock, false);
    }

    private PasswordAttemptLimiter(int maxAttempts, LongSupplier clock, boolean startSweeper) {
        this.maxAttempts = maxAttempts;
        this.clock = clock;
        this.sweeper = startSweeper ? createSweeper() : null;
    }

    private ScheduledExecutorService createSweeper() {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "sfe4j-password-attempt-sweeper");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(this::sweep, SWEEP_INTERVAL_SECONDS, SWEEP_INTERVAL_SECONDS, TimeUnit.SECONDS);
        return executor;
    }

    /**
     * Records an attempt.
     *
     * @return {@code false} when the window already contains the maximum number of attempts
     */
    public boolean tryAcquire(String key) {
        long now = clock.getAsLong();
        if (attempts.size() >= MAX_ENTRIES) {
            sweep();
            if (attempts.size() >= MAX_ENTRIES) {
                Iterator<String> keys = attempts.keySet().iterator();
                if (keys.hasNext()) {
                    attempts.remove(keys.next());
                }
            }
        }

        Deque<Long> window = attempts.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (window) {
            prune(window, now);
            if (window.size() >= maxAttempts) {
                return false;
            }
            window.addLast(now);
            return true;
        }
    }

    /**
     * Clears the window of a key, e.g. after a successful authentication.
     */
    public void reset(String key) {
        attempts.remove(key);
    }

    /**
     * Drops windows that no longer contain an attempt inside the current window.
     */
    public void sweep() {
        long now = clock.getAsLong();
        for (Map.Entry<String, Deque<Long>> entry : attempts.entrySet()) {
            Deque<Long> window = entry.getValue();
            boolean empty;
            synchronized (window) {
                prune(window, now);
                empty = window.isEmpty();
            }
            if (empty) {
                attempts.remove(entry.getKey(), window);
            }
        }
    }

    /**
     * @return number of tracked keys, exposed for tests and diagnostics
     */
    public int trackedKeys() {
        return attempts.size();
    }

    private void prune(Deque<Long> window, long now) {
        while (!window.isEmpty() && now - window.peekFirst() >= WINDOW_MILLIS) {
            window.pollFirst();
        }
    }

    @Override
    public void close() {
        if (sweeper != null) {
            sweeper.shutdownNow();
        }
    }
}
