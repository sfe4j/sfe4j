package com.amoylabs.sfe4j.spring.boot.controller;

import com.amoylabs.sfe4j.spring.boot.Sfe4jTestFixture;
import com.amoylabs.sfe4j.spring.boot.logs.LogFollowManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TextApiControllerTest {

    @TempDir
    Path root;

    private Sfe4jTestFixture fixture;

    private LogFollowManager followManager;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws IOException {
        fixture = Sfe4jTestFixture.create(root);
        fixture.configuration.setLogsEnabled(true);
        fixture.configuration.setLogsDefaultLines(200);
        fixture.configuration.setLogsMaxLines(2000);
        fixture.configuration.setLogsMaxScanBytes(1048576L);
        fixture.configuration.setLogsPollIntervalMs(60000L);
        fixture.configuration.setLogsMaxConnections(20);
        fixture.configuration.setLogsMaxConnectionsPerSession(1);
        fixture.configuration.setLogsMaxConnectionSeconds(1800L);

        followManager = new LogFollowManager(fixture.configuration, fixture.fileAccessService,
                fixture.authorizationService);

        TextApiController controller = new TextApiController();
        controller.setConfiguration(fixture.configuration);
        controller.setFileExplorerService(fixture.fileExplorerService);
        controller.setAuthorizationService(fixture.authorizationService);
        controller.setFollowManager(followManager);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(fixture.exceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        followManager.close();
        fixture.close();
    }

    private Path logFile() throws IOException {
        return Files.writeString(fixture.publicDir.resolve("app.log"), "first\nsecond\nthird\n",
                StandardCharsets.UTF_8);
    }

    @Test
    void tailReadReturnsTheLastLines() throws Exception {
        Path file = logFile();

        mockMvc.perform(get("/sfe4j/api/text").param("file", file.toString()).param("mode", "tail")
                        .param("lines", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("second\nthird\n"))
                .andExpect(jsonPath("$.charset").value("UTF-8"))
                .andExpect(jsonPath("$.lines").value(2))
                .andExpect(jsonPath("$.nextPosition").value(19))
                .andExpect(jsonPath("$.cursor").isNotEmpty());
    }

    @Test
    void headReadIsBoundedByTheRequestedLines() throws Exception {
        Path file = logFile();

        mockMvc.perform(get("/sfe4j/api/text").param("file", file.toString()).param("mode", "head")
                        .param("lines", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("first\n"))
                .andExpect(jsonPath("$.truncated").value(true));
    }

    @Test
    void unsupportedEncodingIsRejected() throws Exception {
        Path file = logFile();

        mockMvc.perform(get("/sfe4j/api/text").param("file", file.toString()).param("charset", "ISO-8859-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void unknownModeIsRejected() throws Exception {
        Path file = logFile();

        mockMvc.perform(get("/sfe4j/api/text").param("file", file.toString()).param("mode", "middle"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void binaryContentIsUnsupported() throws Exception {
        Path binary = Files.write(fixture.publicDir.resolve("dump.bin"), new byte[]{1, 2, 3, 4, 5, 6, 7, 8});

        mockMvc.perform(get("/sfe4j/api/text").param("file", binary.toString()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_PREVIEW"));
    }

    @Test
    void missingFileParameterIsRejected() throws Exception {
        mockMvc.perform(get("/sfe4j/api/text"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void protectedFileRequiresTheDirectoryPassword() throws Exception {
        Path hidden = fixture.secretDir.resolve("hidden.txt");

        mockMvc.perform(get("/sfe4j/api/text").param("file", hidden.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("DIRECTORY_AUTH_REQUIRED"));
    }

    @Test
    void grantedSessionCanReadTheProtectedFile() throws Exception {
        MockHttpSession session = new MockHttpSession();
        fixture.grant(session, fixture.secretDir);

        mockMvc.perform(get("/sfe4j/api/text")
                        .param("file", fixture.secretDir.resolve("hidden.txt").toString())
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("top secret"));
    }

    @Test
    void followIsAuthorizedBeforeTheStreamIsOpened() throws Exception {
        Path hidden = fixture.secretDir.resolve("hidden.txt");

        mockMvc.perform(get("/sfe4j/api/follow").param("file", hidden.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("DIRECTORY_AUTH_REQUIRED"));
    }

    @Test
    void followStartsAnAsynchronousStream() throws Exception {
        Path file = logFile();

        mockMvc.perform(get("/sfe4j/api/follow").param("file", file.toString()).param("lines", "10"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());
    }

    @Test
    void secondFollowConnectionOfTheSameSessionIsRateLimited() throws Exception {
        Path file = logFile();
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(get("/sfe4j/api/follow").param("file", file.toString()).session(session))
                .andExpect(request().asyncStarted());

        mockMvc.perform(get("/sfe4j/api/follow").param("file", file.toString()).session(session))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }
}
