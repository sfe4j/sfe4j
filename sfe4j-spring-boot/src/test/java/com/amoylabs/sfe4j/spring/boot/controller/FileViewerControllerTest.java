package com.amoylabs.sfe4j.spring.boot.controller;

import com.amoylabs.sfe4j.core.vo.TextPreviewVO;
import com.amoylabs.sfe4j.spring.boot.Sfe4jTestFixture;
import com.amoylabs.sfe4j.spring.boot.web.UrlCodec;

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
import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class FileViewerControllerTest {

    @TempDir
    Path root;

    private Sfe4jTestFixture fixture;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws IOException {
        fixture = Sfe4jTestFixture.create(root);

        FileViewerController controller = new FileViewerController();
        controller.setServletContext(fixture.servletContext);
        controller.setFileExplorerService(fixture.fileExplorerService);
        controller.setAuthorizationService(fixture.authorizationService);

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
    void publicFileIsPreviewedAsNoSniffText() throws Exception {
        MvcResult result = mockMvc.perform(get("/file-viewer")
                        .param("file", fixture.publicDir.resolve("note.txt").toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("file-viewer"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn();

        TextPreviewVO preview = (TextPreviewVO) result.getModelAndView().getModel().get("preview");
        assertEquals("hello world", preview.getContent());
    }

    @Test
    void protectedFileRequiresTheDirectoryPassword() throws Exception {
        Path hidden = fixture.secretDir.resolve("hidden.txt");

        mockMvc.perform(get("/file-viewer").param("file", hidden.toString()))
                .andExpect(status().isForbidden())
                .andExpect(view().name("file-error"))
                .andExpect(model().attribute("code", "DIRECTORY_AUTH_REQUIRED"))
                .andExpect(model().attribute("backLink", "/file-explorer?dir="
                        + UrlCodec.encodeQueryValue(fixture.secretDir.toString())))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void grantedSessionCanPreviewTheProtectedFile() throws Exception {
        MockHttpSession session = new MockHttpSession();
        fixture.grant(session, fixture.secretDir);

        mockMvc.perform(get("/file-viewer")
                        .param("file", fixture.secretDir.resolve("hidden.txt").toString())
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("preview"));
    }

    @Test
    void missingFileParameterIsRejected() throws Exception {
        mockMvc.perform(get("/file-viewer").header("Accept", "application/json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("\"code\":\"INVALID_REQUEST\"")));
    }

    @Test
    void blankFileParameterIsRejected() throws Exception {
        mockMvc.perform(get("/file-viewer").param("file", " "))
                .andExpect(status().isBadRequest())
                .andExpect(model().attribute("code", "INVALID_REQUEST"));
    }

    @Test
    void directoryCannotBePreviewed() throws Exception {
        mockMvc.perform(get("/file-viewer").param("file", fixture.publicDir.toString()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(model().attribute("code", "UNSUPPORTED_PREVIEW"));
    }

    @Test
    void binaryFileIsRejected() throws Exception {
        Path binary = Files.write(fixture.publicDir.resolve("binary.bin"), new byte[]{0, 1, 2, 3});

        mockMvc.perform(get("/file-viewer").param("file", binary.toString()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(model().attribute("code", "UNSUPPORTED_PREVIEW"));
    }

    @Test
    void missingFileIsReportedAsNotFound() throws Exception {
        mockMvc.perform(get("/file-viewer").param("file", fixture.publicDir.resolve("nope.txt").toString()))
                .andExpect(status().isNotFound())
                .andExpect(model().attribute("code", "FILE_NOT_FOUND"));
    }

    @Test
    void previewModelNeverCarriesRawFileContentIntoTheModelAsHtml() throws Exception {
        MvcResult result = mockMvc.perform(get("/file-viewer")
                        .param("file", fixture.publicDir.resolve("script.html").toString()))
                .andExpect(status().isOk())
                .andReturn();

        TextPreviewVO preview = (TextPreviewVO) result.getModelAndView().getModel().get("preview");
        assertEquals("<script>alert('xss')</script>", preview.getContent());
        assertFalse(preview.isTruncated());
    }
}
