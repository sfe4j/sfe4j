package com.amoylabs.sfe4j.spring.boot.logs;

import com.amoylabs.sfe4j.core.access.FileAccessContext;
import com.amoylabs.sfe4j.core.access.FileAccessService;
import com.amoylabs.sfe4j.core.access.FileOperation;
import com.amoylabs.sfe4j.core.access.ResolvedPath;
import com.amoylabs.sfe4j.core.configuration.Sfe4jConfiguration;
import com.amoylabs.sfe4j.core.exception.FileAccessErrorCode;
import com.amoylabs.sfe4j.core.exception.FileAccessException;
import com.amoylabs.sfe4j.core.text.TextReadMode;
import com.amoylabs.sfe4j.core.text.TextReadRequest;
import com.amoylabs.sfe4j.core.text.TextReadResult;
import com.amoylabs.sfe4j.spring.boot.access.DirectoryAuthorizationService;
import com.amoylabs.sfe4j.spring.boot.web.JsonWriter;

import jakarta.servlet.http.HttpSession;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

/**
 * Server side of the "follow this file" connection.
 *
 * <p>Every connection is a subscription polled by one shared scheduler thread; no connection owns a
 * thread, and the number of connections, the bytes queued per connection and the lifetime are bounded
 * by {@code sfe4j.logs.*}. A subscription is re-authorized before every read, so an expired grant, a
 * changed protection rule or a rotated session id ends it.</p>
 *
 * <p>Back pressure is estimated: the bytes handed to the container since the last idle round are
 * treated as queued, and a connection that crosses {@code sfe4j.logs.max-queue-bytes} receives a gap
 * event and is closed instead of silently dropping lines. The servlet container does not report the
 * real socket buffer state, so this is a documented approximation and not a flow control guarantee.</p>
 */
public class LogFollowManager implements AutoCloseable {

    /**
     * Event carrying the initial bounded tail and the first cursor.
     */
    public static final String EVENT_SNAPSHOT = "snapshot";

    /**
     * Event carrying newly appended text.
     */
    public static final String EVENT_APPEND = "append";

    /**
     * Event telling the client that the file was replaced, truncated or rotated.
     */
    public static final String EVENT_RESET = "reset";

    /**
     * Event telling the client that data is missing and a new snapshot is needed.
     */
    public static final String EVENT_GAP = "gap";

    /**
     * Final event with a stable error code; the connection is closed afterwards.
     */
    public static final String EVENT_ERROR = "error";

    private static final long HEARTBEAT_INTERVAL_MS = 15_000L;

    private final Sfe4jConfiguration configuration;

    private final FileAccessService fileAccessService;

    private final DirectoryAuthorizationService authorizationService;

    private final FollowCursorCodec cursorCodec;

    private final LongSupplier clock;

    private final ScheduledExecutorService scheduler;

    private final Map<String, Subscription> subscriptions = new ConcurrentHashMap<>();

    private final Map<String, AtomicInteger> perSession = new ConcurrentHashMap<>();

    private final AtomicBoolean stopped = new AtomicBoolean();

    private int activeConnections;

    public LogFollowManager(Sfe4jConfiguration configuration, FileAccessService fileAccessService,
                            DirectoryAuthorizationService authorizationService) {
        this(configuration, fileAccessService, authorizationService, new FollowCursorCodec(),
                System::currentTimeMillis, true);
    }

    LogFollowManager(Sfe4jConfiguration configuration, FileAccessService fileAccessService,
                     DirectoryAuthorizationService authorizationService, FollowCursorCodec cursorCodec,
                     LongSupplier clock, boolean startScheduler) {
        this.configuration = configuration;
        this.fileAccessService = fileAccessService;
        this.authorizationService = authorizationService;
        this.cursorCodec = cursorCodec;
        this.clock = clock;

        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "sfe4j-log-follow");
            thread.setDaemon(true);
            return thread;
        });
        if (startScheduler) {
            long interval = configuration.getLogsPollIntervalMs();
            scheduler.scheduleWithFixedDelay(this::tick, interval, interval, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Opens a follow connection after the caller has authorized the target.
     *
     * @param requestedPath requested path, as it will be authorized again on every round
     * @param charsetName   requested encoding, {@code null} or blank means "detect"
     * @param cursorToken   resume token of a previous connection, may be {@code null}
     * @param lines         number of lines of a snapshot
     * @return the emitter the controller returns to Spring MVC
     * @throws FileAccessException {@code RATE_LIMITED} when the instance or session quota is exhausted
     */
    public SseEmitter subscribe(String requestedPath, String charsetName, String cursorToken, int lines,
                                HttpSession session) {
        if (stopped.get()) {
            throw new FileAccessException(FileAccessErrorCode.ACCESS_DENIED, "Log following is not available.");
        }

        String sessionId = sessionIdOf(session);
        if (!reserve(sessionId)) {
            throw new FileAccessException(FileAccessErrorCode.RATE_LIMITED,
                    "Too many open log connections. Please close one before opening another.");
        }

        FileAccessContext context = authorizationService.contextFor(session);
        Subscription subscription = new Subscription(requestedPath, charsetName, lines, session, sessionId,
                context.getSubjectId(), clock.getAsLong(),
                configuration.getLogsMaxConnectionSeconds() * 1000L);
        final Subscription registered = subscription;

        TextReadResult snapshot;
        try {
            snapshot = readSnapshot(subscription, context, resumePosition(subscription, cursorToken));
        } catch (RuntimeException e) {
            release(sessionId);
            throw e;
        }

        try {
            subscription.emitter.onCompletion(() -> close(registered));
            subscription.emitter.onTimeout(() -> close(registered));
            subscription.emitter.onError(throwable -> close(registered));

            subscriptions.put(subscription.id, subscription);
            applySnapshot(subscription, snapshot);
            send(subscription, EVENT_SNAPSHOT, snapshotPayload(snapshot));
            return subscription.emitter;
        } catch (RuntimeException e) {
            close(subscription);
            throw e;
        }
    }

    private static String snapshotPayload(TextReadResult snapshot) {
        return JsonWriter.object("text", snapshot.getText(), "charset", snapshot.getCharset(),
                "lines", snapshot.getLines(), "size", snapshot.getFileSize(),
                "startPosition", snapshot.getStartPosition(), "nextPosition", snapshot.getNextPosition(),
                "truncated", snapshot.isTruncated(), "cursor", snapshot.getCursor());
    }

    /**
     * Issues a resume token for a read position.
     */
    public String cursor(String subjectId, String path, String charset, String generation, long position) {
        return cursorCodec.encode(subjectId, path, charset, generation, position);
    }

    /**
     * @return the number of subscriptions currently open
     */
    public int activeConnections() {
        synchronized (this) {
            return activeConnections;
        }
    }

    /**
     * @return the number of subscriptions currently open for one session id
     */
    public int activeConnections(String sessionId) {
        AtomicInteger counter = perSession.get(sessionId);
        return counter == null ? 0 : counter.get();
    }

    /**
     * Runs one read round for every subscription. Called by the scheduler and by tests.
     */
    void pollOnce() {
        for (Subscription subscription : new ArrayList<>(subscriptions.values())) {
            try {
                poll(subscription);
            } catch (RuntimeException e) {
                close(subscription);
            }
        }
    }

    /**
     * Stops the scheduler and closes every connection. Called when the application shuts down.
     */
    @Override
    public void close() {
        if (!stopped.compareAndSet(false, true)) {
            return;
        }
        scheduler.shutdownNow();
        for (Subscription subscription : new ArrayList<>(subscriptions.values())) {
            close(subscription);
        }
    }

    private void tick() {
        try {
            pollOnce();
        } catch (RuntimeException ignored) {
            // A failing round must not stop the scheduler.
        }
    }

    private void poll(Subscription subscription) {
        if (subscription.closed.get() || stopped.get()) {
            return;
        }

        long now = clock.getAsLong();
        if (now >= subscription.expiresAt) {
            error(subscription, "EXPIRED", "The connection reached its maximum lifetime.");
            close(subscription);
            return;
        }

        try {
            FileAccessContext context = authorizationService.contextFor(subscription.session);
            ResolvedPath resolved = fileAccessService.authorize(subscription.requestedPath, FileOperation.PREVIEW,
                    context);

            String currentSessionId = sessionIdOf(subscription.session);
            if (!subscription.sessionId.equals(currentSessionId)) {
                // The session id rotated, for example after a new directory authorization. The
                // subscription is terminated instead of migrated, so no unmanageable connection is
                // left behind; the browser reconnects and receives a fresh snapshot.
                send(subscription, EVENT_RESET, JsonWriter.object("reason", "session-rotated"));
                close(subscription);
                return;
            }
            if (subscription.realPath != null && !resolved.getRealPath().equals(subscription.realPath)) {
                reset(subscription, "target-changed", context, resolved);
                return;
            }
            subscription.realPath = resolved.getRealPath();

            TextReadRequest request = new TextReadRequest();
            request.setMode(TextReadMode.HEAD);
            request.setCharset(subscription.charset);
            request.setStartPosition(subscription.position);
            request.setExpectedGeneration(subscription.generation);
            request.setMaxBytes(configuration.getLogsMaxChunkBytes());
            request.setMaxScanBytes(configuration.getLogsMaxScanBytes());

            TextReadResult result = fileAccessService.readTextIncrement(subscription.requestedPath, context, request);
            if (result.isReset()) {
                reset(subscription, result.getResetReason(), context, resolved);
                return;
            }

            String text = result.getText();
            if (text.isEmpty()) {
                // An idle round means the client had a full poll interval to drain the queue.
                subscription.pendingBytes = 0L;
                heartbeat(subscription, now);
                return;
            }

            int payload = text.getBytes(StandardCharsets.UTF_8).length + 64;
            if (subscription.pendingBytes + payload > configuration.getLogsMaxQueueBytes()) {
                send(subscription, EVENT_GAP, JsonWriter.object("reason", "back-pressure"));
                close(subscription);
                return;
            }

            subscription.pendingBytes += payload;
            subscription.position = result.getNextPosition();
            subscription.generation = result.getGeneration();
            subscription.charset = result.getCharset();
            send(subscription, EVENT_APPEND, JsonWriter.object("text", text, "charset", result.getCharset(),
                    "nextPosition", result.getNextPosition(), "size", result.getFileSize(),
                    "cursor", cursor(subscription)));
        } catch (FileAccessException e) {
            error(subscription, e.getCode().name(), e.getMessage());
            close(subscription);
        } catch (IllegalStateException e) {
            error(subscription, "ACCESS_DENIED", "The session is no longer valid.");
            close(subscription);
        }
    }

    private void reset(Subscription subscription, String reason, FileAccessContext context, ResolvedPath resolved) {
        subscription.realPath = resolved.getRealPath();
        send(subscription, EVENT_RESET, JsonWriter.object("reason", reason));
        try {
            TextReadResult snapshot = readSnapshot(subscription, context, 0L);
            applySnapshot(subscription, snapshot);
            send(subscription, EVENT_SNAPSHOT, snapshotPayload(snapshot));
        } catch (FileAccessException e) {
            error(subscription, e.getCode().name(), e.getMessage());
            close(subscription);
        }
    }

    private TextReadResult readSnapshot(Subscription subscription, FileAccessContext context, long startPosition) {
        TextReadRequest request = new TextReadRequest();
        request.setMode(TextReadMode.TAIL);
        request.setCharset(subscription.charset);
        request.setLines(subscription.lines);
        request.setMaxLines(configuration.getLogsMaxLines());
        request.setMaxBytes(configuration.getLogsMaxScanBytes());
        request.setMaxScanBytes(configuration.getLogsMaxScanBytes());
        request.setStartPosition(startPosition);

        TextReadResult result = fileAccessService.readText(subscription.requestedPath, context, request);
        result.setCursor(cursorCodec.encode(subscription.subjectId, subscription.requestedPath,
                result.getCharset(), result.getGeneration(), result.getNextPosition()));
        return result;
    }

    /**
     * @return the byte position of a verified resume token, or {@code 0} for a fresh snapshot
     */
    private long resumePosition(Subscription subscription, String cursorToken) {
        if (cursorToken == null || cursorToken.isBlank()) {
            return 0L;
        }
        FollowCursorCodec.Cursor cursor = cursorCodec.decode(cursorToken, subscription.subjectId,
                subscription.requestedPath, subscription.charset, subscription.generation);
        return cursor == null ? 0L : cursor.position();
    }

    private void heartbeat(Subscription subscription, long now) {
        if (now - subscription.lastSendAt < HEARTBEAT_INTERVAL_MS) {
            return;
        }
        try {
            subscription.emitter.send(SseEmitter.event().comment("heartbeat"));
            subscription.lastSendAt = now;
        } catch (IOException | IllegalStateException e) {
            close(subscription);
        }
    }

    private void send(Subscription subscription, String event, String data) {
        try {
            subscription.emitter.send(SseEmitter.event()
                    .name(event)
                    .data(data)
                    .id(String.valueOf(subscription.eventId.incrementAndGet())));
            subscription.lastSendAt = clock.getAsLong();
        } catch (IOException | IllegalStateException e) {
            close(subscription);
        }
    }

    private void error(Subscription subscription, String code, String message) {
        send(subscription, EVENT_ERROR, JsonWriter.object("code", code, "message", message));
    }

    private void applySnapshot(Subscription subscription, TextReadResult snapshot) {
        subscription.position = snapshot.getNextPosition();
        subscription.generation = snapshot.getGeneration();
        subscription.charset = snapshot.getCharset();
        subscription.pendingBytes = 0L;
    }

    private String cursor(Subscription subscription) {
        return cursorCodec.encode(subscription.subjectId, subscription.requestedPath, subscription.charset,
                subscription.generation, subscription.position);
    }

    private synchronized boolean reserve(String sessionId) {
        if (activeConnections >= configuration.getLogsMaxConnections()) {
            return false;
        }
        AtomicInteger counter = perSession.computeIfAbsent(sessionId, key -> new AtomicInteger());
        if (counter.get() >= configuration.getLogsMaxConnectionsPerSession()) {
            return false;
        }
        counter.incrementAndGet();
        activeConnections++;
        return true;
    }

    private synchronized void release(String sessionId) {
        AtomicInteger counter = perSession.get(sessionId);
        if (counter != null && counter.decrementAndGet() <= 0) {
            perSession.remove(sessionId, counter);
        }
        if (activeConnections > 0) {
            activeConnections--;
        }
    }

    private void close(Subscription subscription) {
        if (!subscription.closed.compareAndSet(false, true)) {
            return;
        }
        subscriptions.remove(subscription.id);
        release(subscription.sessionId);
        try {
            subscription.emitter.complete();
        } catch (IllegalStateException ignored) {
            // The container already completed the response.
        }
    }

    private static String sessionIdOf(HttpSession session) {
        try {
            return session == null || session.getId() == null ? "anonymous" : session.getId();
        } catch (IllegalStateException e) {
            return "invalid";
        }
    }

    private static final class Subscription {

        private final String id = java.util.UUID.randomUUID().toString();

        private final SseEmitter emitter;

        private final String requestedPath;

        private final int lines;

        private final HttpSession session;

        private final String sessionId;

        private final String subjectId;

        private final long expiresAt;

        private final AtomicBoolean closed = new AtomicBoolean();

        private final AtomicInteger eventId = new AtomicInteger();

        private String charset;

        private long position;

        private String generation;

        private long pendingBytes;

        private long lastSendAt;

        private java.nio.file.Path realPath;

        private Subscription(String requestedPath, String charset, int lines, HttpSession session, String sessionId,
                             String subjectId, long now, long lifetimeMillis) {
            this.requestedPath = requestedPath;
            this.charset = charset;
            this.lines = lines;
            this.session = session;
            this.sessionId = sessionId;
            this.subjectId = subjectId;
            this.expiresAt = now + lifetimeMillis;
            this.lastSendAt = now;
            this.emitter = new SseEmitter(lifetimeMillis);
        }
    }

    /**
     * @return the identifiers of the open subscriptions, for tests
     */
    List<String> subscriptionIds() {
        return List.copyOf(subscriptions.keySet());
    }
}
