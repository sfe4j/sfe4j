package com.amoylabs.sfe4j.spring.boot.controller;

import com.amoylabs.sfe4j.core.vo.TextPreviewVO;
import com.amoylabs.sfe4j.spring.boot.Sfe4jTestFixture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * The text page with {@code sfe4j.logs.enabled=true}: mode, lines and encoding are honoured and the
 * page model carries what the follow controls need.
 */
class FileViewerTextPageTest {

    @TempDir
    Path root;

    private Sfe4jTestFixture fixture;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws IOException {
        fixture = Sfe4jTestFixture.create(root);
        fixture.configuration.setLogsEnabled(true);
        fixture.configuration.setLogsDefaultLines(200);
        fixture.configuration.setLogsMaxLines(2000);
        fixture.configuration.setLogsMaxScanBytes(1048576L);

        FileViewerController controller = new FileViewerController();
        controller.setServletContext(fixture.servletContext);
        controller.setFileExplorerService(fixture.fileExplorerService);
        controller.setAuthorizationService(fixture.authorizationService);
        controller.setConfiguration(fixture.configuration);

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

    private Path logFile() throws IOException {
        return Files.writeString(fixture.publicDir.resolve("app.log"), "first\nsecond\nthird\n",
                StandardCharsets.UTF_8);
    }

    private TextPreviewVO preview(MvcResult result) {
        return (TextPreviewVO) result.getModelAndView().getModel().get("preview");
    }

    @Test
    void tailModeReadsTheLastLines() throws Exception {
        Path file = logFile();

        MvcResult result = mockMvc.perform(get("/file-viewer").param("file", file.toString())
                        .param("mode", "tail").param("lines", "2"))
                .andExpect(status().isOk())
                .andExpect(view().name("file-viewer"))
                .andReturn();

        TextPreviewVO preview = preview(result);
        assertEquals("second\nthird\n", preview.getContent());
        assertEquals("tail", preview.getMode());
        assertEquals(2, preview.getLines());
        assertEquals(19L, preview.getSize());
    }

    @Test
    void headModeStaysTheDefault() throws Exception {
        Path file = logFile();

        MvcResult result = mockMvc.perform(get("/file-viewer").param("file", file.toString()))
                .andExpect(status().isOk())
                .andReturn();

        assertEquals("head", preview(result).getMode());
        assertEquals("first\nsecond\nthird\n", preview(result).getContent());
    }

    @Test
    void unknownModeIsRejected() throws Exception {
        Path file = logFile();

        mockMvc.perform(get("/file-viewer").param("file", file.toString()).param("mode", "middle"))
                .andExpect(status().isBadRequest())
                .andExpect(model().attribute("code", "INVALID_REQUEST"));
    }

    @Test
    void hostileContentIsDeliveredAsText() throws Exception {
        Path file = fixture.publicDir.resolve("script.html");

        MvcResult result = mockMvc.perform(get("/file-viewer").param("file", file.toString()))
                .andExpect(status().isOk())
                .andReturn();

        assertEquals("<script>alert('xss')</script>", preview(result).getContent());
    }

    @Test
    void pageModelCarriesTheFollowControls() throws Exception {
        Path file = logFile();

        mockMvc.perform(get("/file-viewer").param("file", file.toString()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("logsEnabled", true))
                .andExpect(model().attribute("defaultLines", 200))
                .andExpect(model().attribute("maxLines", 2000))
                .andExpect(model().attributeExists("charsets"));
    }

    @Test
    void disabledLogsKeepThePreviousPreviewBehaviour() throws Exception {
        fixture.configuration.setLogsEnabled(false);
        Path file = logFile();

        MvcResult result = mockMvc.perform(get("/file-viewer").param("file", file.toString()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("logsEnabled", false))
                .andReturn();

        TextPreviewVO preview = preview(result);
        assertEquals("first\nsecond\nthird\n", preview.getContent());
        assertFalse(preview.isTruncated());
        assertTrue(preview.getCursor() == null);
    }
}
