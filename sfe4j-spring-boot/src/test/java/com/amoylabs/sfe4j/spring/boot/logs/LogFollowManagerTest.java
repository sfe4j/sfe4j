package com.amoylabs.sfe4j.spring.boot.logs;

import com.amoylabs.sfe4j.core.exception.FileAccessErrorCode;
import com.amoylabs.sfe4j.core.exception.FileAccessException;
import com.amoylabs.sfe4j.spring.boot.Sfe4jTestFixture;
import com.amoylabs.sfe4j.spring.boot.controller.TextApiController;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LogFollowManagerTest {

    @TempDir
    Path root;

    private Sfe4jTestFixture fixture;

    private LogFollowManager manager;

    private MockMvc streamMvc;

    private final long[] clock = {1_000_000L};

    @BeforeEach
    void setUp() throws IOException {
        fixture = Sfe4jTestFixture.create(root);
        fixture.configuration.setLogsMaxConnections(2);
        fixture.configuration.setLogsMaxConnectionsPerSession(1);
        fixture.configuration.setLogsMaxConnectionSeconds(60L);
        fixture.configuration.setLogsPollIntervalMs(1000L);
        manager = new LogFollowManager(fixture.configuration, fixture.fileAccessService,
                fixture.authorizationService, new FollowCursorCodec(), () -> clock[0], false);
    }

    @AfterEach
    void tearDown() {
        manager.close();
        fixture.close();
    }

    private Path logFile() throws IOException {
        Path file = fixture.publicDir.resolve("app.log");
        if (!Files.exists(file)) {
            Files.writeString(file, "line-1\n", StandardCharsets.UTF_8);
        }
        return file;
    }

    @Test
    void subscriptionIsCounted() throws IOException {
        manager.subscribe(logFile().toString(), null, null, 200, new MockHttpSession());

        assertEquals(1, manager.activeConnections());
        assertEquals(1, manager.subscriptionIds().size());
    }

    @Test
    void instanceQuotaIsEnforced() throws IOException {
        manager.subscribe(logFile().toString(), null, null, 200, new MockHttpSession());
        manager.subscribe(logFile().toString(), null, null, 200, new MockHttpSession());

        FileAccessException exception = assertThrows(FileAccessException.class,
                () -> manager.subscribe(logFile().toString(), null, null, 200, new MockHttpSession()));
        assertEquals(FileAccessErrorCode.RATE_LIMITED, exception.getCode());
        assertEquals(2, manager.activeConnections());
    }

    @Test
    void sessionQuotaIsEnforced() throws IOException {
        MockHttpSession session = new MockHttpSession();
        manager.subscribe(logFile().toString(), null, null, 200, session);

        FileAccessException exception = assertThrows(FileAccessException.class,
                () -> manager.subscribe(logFile().toString(), null, null, 200, session));
        assertEquals(FileAccessErrorCode.RATE_LIMITED, exception.getCode());
        assertEquals(1, manager.activeConnections(session.getId()));
    }

    @Test
    void expiredConnectionIsClosedOnTheNextRound() throws IOException {
        manager.subscribe(logFile().toString(), null, null, 200, new MockHttpSession());

        clock[0] += 61_000L;
        manager.pollOnce();

        assertEquals(0, manager.activeConnections());
    }

    @Test
    void closedManagerRefusesNewConnections() throws IOException {
        manager.close();

        FileAccessException exception = assertThrows(FileAccessException.class,
                () -> manager.subscribe(logFile().toString(), null, null, 200, new MockHttpSession()));
        assertEquals(FileAccessErrorCode.ACCESS_DENIED, exception.getCode());
    }

    @Test
    void protectedDirectoryIsRefused() {
        Path hidden = fixture.secretDir.resolve("hidden.txt");

        FileAccessException exception = assertThrows(FileAccessException.class,
                () -> manager.subscribe(hidden.toString(), null, null, 200, new MockHttpSession()));
        assertEquals(FileAccessErrorCode.DIRECTORY_AUTH_REQUIRED, exception.getCode());
        assertEquals(0, manager.activeConnections());
    }

    @Test
    void binaryContentIsRefusedAndReleasesTheQuota() throws IOException {
        Path binary = Files.write(fixture.publicDir.resolve("dump.bin"), new byte[]{1, 2, 3, 4, 5, 6, 7, 8});

        FileAccessException exception = assertThrows(FileAccessException.class,
                () -> manager.subscribe(binary.toString(), null, null, 200, new MockHttpSession()));
        assertEquals(FileAccessErrorCode.UNSUPPORTED_PREVIEW, exception.getCode());
        assertEquals(0, manager.activeConnections());
    }

    @Test
    void rotationEndsTheSubscriptionWithASnapshot() throws IOException {
        Path file = logFile();
        manager.subscribe(file.toString(), null, null, 200, new MockHttpSession());

        Path replacement = Files.writeString(root.resolve("replacement.log"), "fresh\n", StandardCharsets.UTF_8);
        Files.move(replacement, file, StandardCopyOption.REPLACE_EXISTING);

        manager.pollOnce();

        assertEquals(1, manager.activeConnections(),
                "a rotation is recovered with a new snapshot instead of closing the connection");
    }

    @Test
    void appendedBytesArePolled() throws IOException {
        Path file = logFile();
        manager.subscribe(file.toString(), null, null, 200, new MockHttpSession());

        Files.write(file, "line-2\n".getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND);
        manager.pollOnce();

        assertEquals(1, manager.activeConnections());
        assertTrue(manager.subscriptionIds().size() == 1);
    }

    /**
     * The stream content is read through the real controller and the real async dispatch, so the
     * event names and the payload escaping are covered as well.
     */
    @Test
    void snapshotAndAppendEventsAreWrittenToTheStream() throws Exception {
        Path file = logFile();
        MvcResult result = follow(file);

        Files.write(file, "line-2\n".getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND);
        manager.pollOnce();

        String body = result.getResponse().getContentAsString();

        assertTrue(body.contains("event:snapshot"), body);
        assertTrue(body.contains("event:append"), body);
        assertTrue(body.contains("line-1"), body);
        assertTrue(body.contains("line-2"), body);
        assertFalse(body.contains("data:line"), "the payload is JSON encoded, never a raw log line");
    }

    @Test
    void rotationProducesAResetEventAndANewSnapshot() throws Exception {
        Path file = logFile();
        MvcResult result = follow(file);

        Path replacement = Files.writeString(root.resolve("rotated.log"), "fresh content\n",
                StandardCharsets.UTF_8);
        Files.move(replacement, file, StandardCopyOption.REPLACE_EXISTING);
        manager.pollOnce();

        String body = result.getResponse().getContentAsString();

        assertTrue(body.contains("event:reset"), body);
        assertTrue(body.contains("fresh content"), body);
    }

    /**
     * A client that does not keep up receives a gap event and is disconnected instead of losing
     * lines silently: the queue is only assumed to drain in an idle round, so consecutive full
     * rounds cross the configured bound.
     */
    @Test
    void overrunQueueProducesAGapAndClosesTheConnection() throws Exception {
        fixture.configuration.setLogsMaxQueueBytes(4096);
        fixture.configuration.setLogsMaxChunkBytes(1024);
        Path file = logFile();
        MvcResult result = follow(file);

        byte[] chunk = new byte[1024];
        java.util.Arrays.fill(chunk, (byte) 'x');
        for (int round = 0; round < 8 && manager.activeConnections() > 0; round++) {
            Files.write(file, chunk, StandardOpenOption.APPEND);
            Files.write(file, "\n".getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND);
            manager.pollOnce();
        }

        String body = result.getResponse().getContentAsString();

        assertTrue(body.contains("event:gap"), body);
        assertEquals(0, manager.activeConnections(), "an overrunning connection is closed");
    }

    @Test
    void rotatedSessionIdEndsTheConnection() throws Exception {
        Path file = logFile();
        MockHttpSession session = new MockHttpSession();
        MvcResult result = mockMvc().perform(get("/sfe4j/api/follow").param("file", file.toString())
                        .session(session))
                .andExpect(request().asyncStarted())
                .andReturn();

        session.changeSessionId();
        manager.pollOnce();

        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains("session-rotated"), body);
        assertEquals(0, manager.activeConnections(), "a rotated session id must not leave a subscription");
    }

    @Test
    void closeReleasesEveryConnection() throws IOException {
        manager.subscribe(logFile().toString(), null, null, 200, new MockHttpSession());
        manager.subscribe(logFile().toString(), null, null, 200, new MockHttpSession());
        assertEquals(2, manager.activeConnections());

        manager.close();

        assertEquals(0, manager.activeConnections());
    }

    private MvcResult follow(Path file) throws Exception {
        return mockMvc().perform(get("/sfe4j/api/follow").param("file", file.toString())
                        .session(new MockHttpSession()))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();
    }

    private MockMvc mockMvc() {
        if (streamMvc == null) {
            TextApiController controller = new TextApiController();
            controller.setConfiguration(fixture.configuration);
            controller.setFileExplorerService(fixture.fileExplorerService);
            controller.setAuthorizationService(fixture.authorizationService);
            controller.setFollowManager(manager);
            streamMvc = MockMvcBuilders.standaloneSetup(controller)
                    .setControllerAdvice(fixture.exceptionHandler())
                    .build();
        }
        return streamMvc;
    }
}
