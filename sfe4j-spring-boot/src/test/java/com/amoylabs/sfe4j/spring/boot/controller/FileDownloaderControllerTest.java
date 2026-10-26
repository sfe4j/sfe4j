package com.amoylabs.sfe4j.spring.boot.controller;

import com.amoylabs.sfe4j.spring.boot.Sfe4jTestFixture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FileDownloaderControllerTest {

    @TempDir
    Path root;

    private Sfe4jTestFixture fixture;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws IOException {
        fixture = Sfe4jTestFixture.create(root, "", 1, 5);

        FileDownloaderController controller = new FileDownloaderController();
        controller.setFileExplorerService(fixture.fileExplorerService);
        controller.setAuthorizationService(fixture.authorizationService);
        controller.setConcurrencyLimiter(fixture.downloadConcurrencyLimiter);

        InternalResourceViewResolver viewResolver = new InternalResourceViewResolver();
        viewResolver.setPrefix("/WEB-INF/views/");
        viewResolver.setSuffix(".jsp");

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(fixture.exceptionHandler())
                .setViewResolvers(viewResolver)
                .build();
    }

    @AfterEach
    void tearDown() {
        fixture.close();
    }

    @Test
    void downloadStreamsTheFileWithAsciiSafeHeaders() throws Exception {
        byte[] content = "line one\nline two\n".getBytes(StandardCharsets.UTF_8);
        Path file = Files.write(fixture.publicDir.resolve("report.txt"), content);

        MvcResult result = mockMvc.perform(get("/file-downloader").param("file", file.toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/octet-stream"))
                .andExpect(header().string("Content-Length", String.valueOf(content.length)))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn();

        assertArrayEquals(content, result.getResponse().getContentAsByteArray());
        String disposition = result.getResponse().getHeader("Content-Disposition");
        assertTrue(disposition != null && disposition.startsWith("attachment"), disposition);
        assertTrue(disposition.contains("report.txt"), disposition);
    }

    @Test
    void nonAsciiFileNameIsEncodedAndCannotBreakTheHeader() throws Exception {
        Path file = Files.write(fixture.publicDir.resolve("中文 报告.txt"), "x".getBytes(StandardCharsets.UTF_8));

        MvcResult result = mockMvc.perform(get("/file-downloader").param("file", file.toString()))
                .andExpect(status().isOk())
                .andReturn();

        String disposition = result.getResponse().getHeader("Content-Disposition");
        assertTrue(disposition.contains("filename*=UTF-8''"), disposition);
        assertTrue(disposition.contains("%E4%B8%AD%E6%96%87"), disposition);
        assertTrue(!disposition.contains("\r") && !disposition.contains("\n"), disposition);
    }

    @Test
    void fileNameWithControlCharacterIsRejectedBeforeTheHeaderIsBuilt() throws Exception {
        Path file = Files.write(fixture.publicDir.resolve("evil\r\nname.txt"), "x".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(get("/file-downloader").param("file", file.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(model().attribute("code", "INVALID_REQUEST"));
    }

    @Test
    void permitIsReleasedWhenTheTransferEnds() throws Exception {
        Path file = Files.write(fixture.publicDir.resolve("small.txt"), "abc".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(get("/file-downloader").param("file", file.toString()))
                .andExpect(status().isOk());

        assertEquals(1, fixture.downloadConcurrencyLimiter.availablePermits());
    }

    @Test
    void exceedingTheConcurrentLimitReturnsTooManyRequests() throws Exception {
        Path file = Files.write(fixture.publicDir.resolve("small.txt"), "abc".getBytes(StandardCharsets.UTF_8));
        assertTrue(fixture.downloadConcurrencyLimiter.tryAcquire(), "occupy the only permit");

        try {
            mockMvc.perform(get("/file-downloader").param("file", file.toString()))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(model().attribute("code", "RATE_LIMITED"));
        } finally {
            fixture.downloadConcurrencyLimiter.release();
        }
    }

    @Test
    void protectedFileRequiresTheDirectoryPassword() throws Exception {
        mockMvc.perform(get("/file-downloader")
                        .param("file", fixture.secretDir.resolve("hidden.txt").toString()))
                .andExpect(status().isForbidden())
                .andExpect(model().attribute("code", "DIRECTORY_AUTH_REQUIRED"));
    }

    @Test
    void grantedSessionCanDownloadTheProtectedFile() throws Exception {
        MockHttpSession session = new MockHttpSession();
        fixture.grant(session, fixture.secretDir);

        mockMvc.perform(get("/file-downloader")
                        .param("file", fixture.secretDir.resolve("hidden.txt").toString())
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(content().bytes("top secret".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void directoryCannotBeDownloaded() throws Exception {
        mockMvc.perform(get("/file-downloader").param("file", fixture.publicDir.toString()))
                .andExpect(status().isForbidden())
                .andExpect(model().attribute("code", "ACCESS_DENIED"));
    }

    @Test
    void missingFileParameterIsRejectedAsJson() throws Exception {
        mockMvc.perform(get("/file-downloader").header("Accept", "application/json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("\"code\":\"INVALID_REQUEST\"")))
                .andExpect(content().string(not(containsString("java."))));
    }
}
