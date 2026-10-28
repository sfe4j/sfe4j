package com.amoylabs.sfe4j.spring.boot.web;

import com.amoylabs.sfe4j.core.access.FileAccessService;
import com.amoylabs.sfe4j.core.configuration.Sfe4jConfiguration;
import com.amoylabs.sfe4j.core.vo.FileTreeVO;
import com.amoylabs.sfe4j.core.vo.TextPreviewVO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Renders the real templates. This is the regression test for the former {@code noautoesc} block: file
 * content and file names must never reach the page as markup.
 */
class TemplateRenderingTest {

    @TempDir
    Path root;

    private freemarker.template.Configuration freeMarkerConfiguration;

    @BeforeEach
    void setUp() {
        freeMarkerConfiguration = new freemarker.template.Configuration(
                freemarker.template.Configuration.VERSION_2_3_34);
        freeMarkerConfiguration.setClassLoaderForTemplateLoading(getClass().getClassLoader(), "/templates");
        freeMarkerConfiguration.setDefaultEncoding(StandardCharsets.UTF_8.name());
        freeMarkerConfiguration.setLogTemplateExceptions(false);
    }

    private String render(String templateName, Map<String, Object> model) throws Exception {
        StringWriter writer = new StringWriter();
        freeMarkerConfiguration.getTemplate(templateName).process(model, writer);
        return writer.toString();
    }

    private Map<String, Object> meta() {
        Map<String, Object> meta = new HashMap<>();
        meta.put("title", "SFE4J");
        meta.put("description", "test");
        meta.put("quickLinks", Map.of());
        return meta;
    }

    @Test
    void fileContentIsEscapedInTheViewer() throws Exception {
        TextPreviewVO preview = new TextPreviewVO();
        preview.setFileName("attack.html");
        preview.setContent("<script>alert('xss')</script>");
        preview.setEncoding("UTF-8");
        preview.setParentPath("/data");
        preview.setSize(27);
        preview.setMaxBytes(1024);

        Map<String, Object> model = new HashMap<>();
        model.put("ctx", "");
        model.put("url", new LinkBuilder(""));
        model.put("meta", meta());
        model.put("preview", preview);

        String html = render("file-viewer.ftlh", model);

        assertFalse(html.contains("<script>alert"), "file content must not be interpreted as markup");
        assertTrue(html.contains("&lt;script&gt;"));
        assertTrue(html.contains("class=\"sfe4j-content\""));
        assertFalse(html.contains("id=\"sfe4j-follow\""), "follow needs sfe4j.logs.enabled=true");
    }

    @Test
    void theViewerKeepsNumbersAndTextInsideOneScrollingContainer() throws Exception {
        TextPreviewVO preview = new TextPreviewVO();
        preview.setFileName("app.log");
        preview.setContent("line\n");
        preview.setEncoding("UTF-8");
        preview.setParentPath("/data/logs");
        preview.setSize(5);
        preview.setMaxBytes(1048576);

        Map<String, Object> model = new HashMap<>();
        model.put("ctx", "");
        model.put("url", new LinkBuilder(""));
        model.put("meta", meta());
        model.put("preview", preview);

        String html = render("file-viewer.ftlh", model);

        // Numbers and text used to be two sibling columns, so a wrapped line pushed every number
        // below it out of sync. They now share one row and one scroll container.
        assertTrue(html.contains("class=\"sfe4j-content\""));
        assertTrue(html.contains("class=\"sfe4j-plain\""));
        assertFalse(html.contains("sfe4j-gutter"), "numbers must not be a second scrolling column");
    }

    @Test
    void followControlsAreRenderedWhenLogFollowingIsEnabled() throws Exception {
        TextPreviewVO preview = new TextPreviewVO();
        preview.setFileName("app.log");
        preview.setContent("line\n");
        preview.setEncoding("UTF-8");
        preview.setParentPath("/data/logs");
        preview.setSize(5);
        preview.setMaxBytes(1048576);
        preview.setMode("tail");

        Map<String, Object> model = new HashMap<>();
        model.put("ctx", "");
        model.put("url", new LinkBuilder(""));
        model.put("meta", meta());
        model.put("preview", preview);
        model.put("logsEnabled", true);
        model.put("charsets", List.of("UTF-8", "UTF-16LE"));
        model.put("defaultLines", 200);
        model.put("maxLines", 2000);

        String html = render("file-viewer.ftlh", model);

        assertTrue(html.contains("id=\"sfe4j-follow\""));
        assertTrue(html.contains("data-logs-enabled=\"true\""));
        assertTrue(html.contains("data-api=\"/sfe4j/api\""));
        assertTrue(html.contains(">UTF-16LE<"), "every supported encoding is offered");
        assertTrue(html.contains("js/sfe4j-text-viewer.js"));
    }

    @Test
    void truncationAndDecodingIssuesAreAnnounced() throws Exception {
        TextPreviewVO preview = new TextPreviewVO();
        preview.setFileName("big.log");
        preview.setContent("head");
        preview.setEncoding("UTF-8");
        preview.setParentPath("/data");
        preview.setSize(10_000_000L);
        preview.setMaxBytes(1024L);
        preview.setTruncated(true);
        preview.setDecodingIssue(true);

        Map<String, Object> model = new HashMap<>();
        model.put("ctx", "");
        model.put("url", new LinkBuilder(""));
        model.put("meta", meta());
        model.put("preview", preview);

        String html = render("file-viewer.ftlh", model);

        assertTrue(html.contains("Content truncated"));
        assertTrue(html.contains("1024"));
        assertTrue(html.contains("replacement characters"));
    }

    @Test
    void listedFileNamesAndLinksAreEscapedAndEncoded() throws Exception {
        Files.writeString(root.resolve("<img src=x onerror=alert(1)>.txt"), "x");
        Files.createDirectory(root.resolve("my dir"));

        Sfe4jConfiguration configuration = new Sfe4jConfiguration();
        configuration.setBaseDirPath(root.toString());
        configuration.setRestrictToBaseDir(true);
        FileAccessService fileAccessService = new FileAccessService(configuration);
        FileTreeVO fileTree = fileAccessService.list(root.toString(),
                com.amoylabs.sfe4j.core.access.FileAccessContext.anonymous());

        Map<String, Object> model = new HashMap<>();
        model.put("ctx", "");
        model.put("url", new LinkBuilder(""));
        model.put("meta", meta());
        model.put("fileTree", fileTree);
        model.put("csrfParameter", "_csrf");
        model.put("csrfToken", "token");

        String html = render("file-explorer.ftlh", model);

        assertFalse(html.contains("<img src=x"), "file names must not be interpreted as markup");
        assertTrue(html.contains("&lt;img src=x"));
        assertTrue(html.contains("my%20dir"), "directory links must be percent encoded");
        assertTrue(html.contains("my dir"), "the visible name stays readable");
    }

    @Test
    void errorMessageIsEscaped() throws Exception {
        Map<String, Object> model = new HashMap<>();
        model.put("ctx", "/app");
        model.put("status", 403);
        model.put("code", "ACCESS_DENIED");
        model.put("message", "<b>denied</b>");
        model.put("requestId", "abc-123");
        model.put("meta", meta());

        String html = render("file-error.ftlh", model);

        assertFalse(html.contains("<b>denied</b>"));
        assertTrue(html.contains("&lt;b&gt;denied"));
        assertTrue(html.contains("abc-123"));
        assertTrue(html.contains("/app/file-explorer"));
    }
}
