package com.amoylabs.sfe4j.spring.boot.controller;

import com.amoylabs.sfe4j.core.vo.BreadcrumbVO;
import com.amoylabs.sfe4j.core.vo.FileVO;
import com.amoylabs.sfe4j.spring.boot.Sfe4jTestFixture;
import com.amoylabs.sfe4j.spring.boot.web.LinkBuilder;

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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class FileExplorerControllerTest {

    @TempDir
    Path root;

    private Sfe4jTestFixture fixture;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws IOException {
        fixture = Sfe4jTestFixture.create(root);
        mockMvc = buildMockMvc(fixture);
    }

    @AfterEach
    void tearDown() {
        fixture.close();
    }

    private MockMvc buildMockMvc(Sfe4jTestFixture target) {
        FileExplorerController controller = new FileExplorerController();
        controller.setServletContext(target.servletContext);
        controller.setFileExplorerService(target.fileExplorerService);
        controller.setAuthorizationService(target.authorizationService);
        controller.setCsrfSupport(target.csrfSupport);

        InternalResourceViewResolver viewResolver = new InternalResourceViewResolver();
        viewResolver.setPrefix("/WEB-INF/views/");
        viewResolver.setSuffix(".jsp");

        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(target.exceptionHandler())
                .setViewResolvers(viewResolver)
                .build();
    }

    @Test
    void publicDirectoryIsListed() throws Exception {
        mockMvc.perform(get("/file-explorer").param("dir", fixture.publicDir.toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("file-explorer"))
                .andExpect(model().attributeExists("fileTree"));
    }

    @Test
    void protectedDirectoryShowsTheChallengeInsteadOfAServerError() throws Exception {
        mockMvc.perform(get("/file-explorer").param("dir", fixture.secretDir.toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("file-explorer"))
                .andExpect(model().attribute("requiresPassword", true))
                .andExpect(model().attribute("targetLabel", "secret"));
    }

    @Test
    void challengePageDoesNotLeakTheAbsolutePath() throws Exception {
        MvcResult result = mockMvc.perform(get("/file-explorer").param("dir", fixture.secretDir.toString()))
                .andReturn();

        Object label = result.getModelAndView().getModel().get("targetLabel");
        assertEquals("secret", label);
    }

    @Test
    void directoryOutsideTheBaseDirectoryIsRejected() throws Exception {
        mockMvc.perform(get("/file-explorer").param("dir", root.getParent().toString()))
                .andExpect(status().isForbidden())
                .andExpect(view().name("file-error"))
                .andExpect(model().attribute("code", "ACCESS_DENIED"));
    }

    @Test
    void passwordSubmissionWithoutCsrfTokenIsRejected() throws Exception {
        mockMvc.perform(post("/file-explorer")
                        .param("dir", fixture.secretDir.toString())
                        .param("pwd", "secret-pass"))
                .andExpect(status().isBadRequest())
                .andExpect(model().attribute("code", "INVALID_REQUEST"));
    }

    @Test
    void wrongPasswordShowsTheErrorOnTheChallengePage() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String token = fetchToken(session, fixture.secretDir);

        mockMvc.perform(post("/file-explorer")
                        .param("dir", fixture.secretDir.toString())
                        .param("pwd", "wrong")
                        .param("_csrf", token)
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("file-explorer"))
                .andExpect(model().attribute("requiresPassword", true))
                .andExpect(model().attribute("pwdError", true));
    }

    @Test
    void correctPasswordRedirectsAndUnlocksTheDirectory() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String token = fetchToken(session, fixture.secretDir);

        mockMvc.perform(post("/file-explorer")
                        .param("dir", fixture.secretDir.toString())
                        .param("pwd", "secret-pass")
                        .param("_csrf", token)
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/file-explorer?dir="
                        + com.amoylabs.sfe4j.spring.boot.web.UrlCodec.encodeQueryValue(fixture.secretDir.toString())));

        MvcResult result = mockMvc.perform(get("/file-explorer")
                        .param("dir", fixture.secretDir.toString())
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(model().attributeDoesNotExist("requiresPassword"))
                .andReturn();

        Object tree = result.getModelAndView().getModel().get("fileTree");
        assertNotNull(tree);
        assertTrue(((com.amoylabs.sfe4j.core.vo.FileTreeVO) tree).getFiles().stream()
                .map(FileVO::getName).anyMatch("hidden.txt"::equals));
    }

    @Test
    void passwordAttemptsAreRateLimited() throws Exception {
        try (Sfe4jTestFixture limited = Sfe4jTestFixture.create(root, "", 8, 3)) {
            MockMvc limitedMvc = buildMockMvc(limited);
            MockHttpSession session = new MockHttpSession();

            for (int attempt = 0; attempt < 3; attempt++) {
                String token = fetchToken(limitedMvc, session, limited.secretDir);
                limitedMvc.perform(post("/file-explorer")
                                .param("dir", limited.secretDir.toString())
                                .param("pwd", "wrong")
                                .param("_csrf", token)
                                .session(session))
                        .andExpect(status().isOk());
            }

            String token = fetchToken(limitedMvc, session, limited.secretDir);
            limitedMvc.perform(post("/file-explorer")
                            .param("dir", limited.secretDir.toString())
                            .param("pwd", "wrong")
                            .param("_csrf", token)
                            .session(session))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(model().attribute("code", "RATE_LIMITED"));
        }
    }

    @Test
    void logoutRemovesOnlyTheSfe4jGrant() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String token = fetchToken(session, fixture.secretDir);
        mockMvc.perform(post("/file-explorer")
                        .param("dir", fixture.secretDir.toString())
                        .param("pwd", "secret-pass")
                        .param("_csrf", token)
                        .session(session))
                .andExpect(status().is3xxRedirection());

        String rotated = fetchToken(session, fixture.secretDir);
        mockMvc.perform(post("/file-explorer/logout")
                        .param("dir", fixture.secretDir.toString())
                        .param("_csrf", rotated)
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/file-explorer?dir="
                        + com.amoylabs.sfe4j.spring.boot.web.UrlCodec.encodeQueryValue(fixture.secretDir.toString())));

        mockMvc.perform(get("/file-explorer")
                        .param("dir", fixture.secretDir.toString())
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(model().attribute("requiresPassword", true));
    }

    @Test
    void linksArePercentEncodedForSpecialCharacters() throws Exception {
        Path special = Files.createDirectory(fixture.publicDir.resolve("we ird&100%#dir"));

        MvcResult result = mockMvc.perform(get("/file-explorer").param("dir", fixture.publicDir.toString()))
                .andReturn();

        LinkBuilder links = (LinkBuilder) result.getModelAndView().getModel().get("url");
        String listing = links.listing(special.toAbsolutePath().toString());

        assertTrue(listing.contains("%20"), listing);
        assertTrue(listing.contains("%26"), listing);
        assertTrue(listing.contains("%25"), listing);
        assertTrue(listing.contains("%23"), listing);
        assertTrue(listing.contains("%2F"), listing);
    }

    @Test
    void breadcrumbsUseCumulativePathsForRepeatedNames() throws Exception {
        Path repeated = Files.createDirectories(fixture.publicDir.resolve("same").resolve("same"));

        MvcResult result = mockMvc.perform(get("/file-explorer").param("dir", repeated.toString()))
                .andReturn();

        com.amoylabs.sfe4j.core.vo.FileTreeVO tree =
                (com.amoylabs.sfe4j.core.vo.FileTreeVO) result.getModelAndView().getModel().get("fileTree");
        List<BreadcrumbVO> breadcrumbs = tree.getBreadcrumbs();

        String first = breadcrumbs.get(breadcrumbs.size() - 2).getPath();
        String second = breadcrumbs.get(breadcrumbs.size() - 1).getPath();
        assertTrue(second.startsWith(first));
        assertTrue(!first.equals(second));
    }

    @Test
    void accessDeniedIsReportedAsJsonWhenRequested() throws Exception {
        mockMvc.perform(get("/file-explorer")
                        .param("dir", root.getParent().toString())
                        .header("Accept", "application/json"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"code\":\"ACCESS_DENIED\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"requestId\"")));
    }

    private String fetchToken(MockHttpSession session, Path dir) throws Exception {
        return fetchToken(mockMvc, session, dir);
    }

    private String fetchToken(MockMvc mvc, MockHttpSession session, Path dir) throws Exception {
        MvcResult result = mvc.perform(get("/file-explorer").param("dir", dir.toString()).session(session))
                .andReturn();
        String token = Sfe4jTestFixture.csrfTokenFrom(result.getModelAndView());
        assertNotNull(token, "the challenge page must render a CSRF token");
        return token;
    }
}
