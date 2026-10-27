package com.amoylabs.sfe4j.spring.boot.controller;

import com.amoylabs.sfe4j.core.access.FileAccessContext;
import com.amoylabs.sfe4j.core.access.FileOperation;
import com.amoylabs.sfe4j.core.configuration.Sfe4jConfiguration;
import com.amoylabs.sfe4j.core.exception.FileAccessErrorCode;
import com.amoylabs.sfe4j.core.exception.FileAccessException;
import com.amoylabs.sfe4j.core.service.FileExplorerService;
import com.amoylabs.sfe4j.core.text.TextReadMode;
import com.amoylabs.sfe4j.core.text.TextReadRequest;
import com.amoylabs.sfe4j.core.text.TextReadResult;
import com.amoylabs.sfe4j.spring.boot.access.DirectoryAuthorizationService;
import com.amoylabs.sfe4j.spring.boot.logs.LogFollowManager;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * JSON text read API and the server sent event stream of the follow mode.
 *
 * <p>Both endpoints read through the P0 access facade: the {@code file} parameter is resolved and
 * authorized exactly like the listing, preview and download entry points, and the follow stream is
 * authorized again before every read. When {@code sfe4j.logs.enabled} is {@code false} this controller
 * is not registered at all.</p>
 */
@RestController
@RequestMapping("/sfe4j/api")
@ConditionalOnProperty(prefix = "sfe4j.logs", name = "enabled", havingValue = "true")
public class TextApiController {

    private Sfe4jConfiguration configuration;

    private FileExplorerService fileExplorerService;

    private DirectoryAuthorizationService authorizationService;

    private LogFollowManager followManager;

    /**
     * Bounded text read: head or tail, a number of lines and an encoding.
     */
    @GetMapping("/text")
    public TextReadResult text(@RequestParam("file") String file,
                               @RequestParam(value = "mode", required = false) String mode,
                               @RequestParam(value = "lines", required = false) Integer lines,
                               @RequestParam(value = "charset", required = false) String charset,
                               HttpSession session, HttpServletResponse response) {
        requireFile(file);
        noStore(response);

        TextReadRequest request = new TextReadRequest();
        request.setMode(readMode(mode));
        request.setCharset(charset);
        request.setLines(lines == null ? configuration.getLogsDefaultLines() : lines);
        request.setMaxLines(configuration.getLogsMaxLines());
        request.setMaxBytes(configuration.getLogsMaxScanBytes());
        request.setMaxScanBytes(configuration.getLogsMaxScanBytes());

        FileAccessContext context = authorizationService.contextFor(session);
        TextReadResult result = fileExplorerService.readText(file, context, request);
        result.setCursor(followManager.cursor(context.getSubjectId(), file, result.getCharset(),
                result.getGeneration(), result.getNextPosition()));
        return result;
    }

    /**
     * Opens a follow stream for an authorized file.
     *
     * <p>The target is authorized before the stream is opened, so an unauthorized caller receives the
     * documented error status instead of an open connection.</p>
     */
    @GetMapping(value = "/follow", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter follow(@RequestParam("file") String file,
                             @RequestParam(value = "charset", required = false) String charset,
                             @RequestParam(value = "lines", required = false) Integer lines,
                             @RequestParam(value = "cursor", required = false) String cursor,
                             HttpSession session, HttpServletResponse response) {
        requireFile(file);
        noStore(response);

        FileAccessContext context = authorizationService.contextFor(session);
        fileExplorerService.getFileAccessService().authorize(file, FileOperation.PREVIEW, context);

        return followManager.subscribe(file, charset, cursor,
                lines == null ? configuration.getLogsDefaultLines() : lines, session);
    }

    private static void requireFile(String file) {
        if (file == null || file.isBlank()) {
            throw new FileAccessException(FileAccessErrorCode.INVALID_REQUEST, "The 'file' parameter is required.");
        }
    }

    private static TextReadMode readMode(String mode) {
        try {
            return TextReadMode.from(mode);
        } catch (IllegalArgumentException e) {
            throw new FileAccessException(FileAccessErrorCode.INVALID_REQUEST,
                    "The 'mode' parameter must be 'head' or 'tail'.");
        }
    }

    private static void noStore(HttpServletResponse response) {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Cache-Control", "no-store");
    }

    @Autowired
    public void setConfiguration(Sfe4jConfiguration configuration) {
        this.configuration = configuration;
    }

    @Autowired
    public void setFileExplorerService(FileExplorerService fileExplorerService) {
        this.fileExplorerService = fileExplorerService;
    }

    @Autowired
    public void setAuthorizationService(DirectoryAuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    @Autowired
    public void setFollowManager(LogFollowManager followManager) {
        this.followManager = followManager;
    }
}
