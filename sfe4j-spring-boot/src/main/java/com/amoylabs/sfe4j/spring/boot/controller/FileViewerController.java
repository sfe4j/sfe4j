package com.amoylabs.sfe4j.spring.boot.controller;

import com.amoylabs.sfe4j.core.access.FileAccessContext;
import com.amoylabs.sfe4j.core.configuration.Sfe4jConfiguration;
import com.amoylabs.sfe4j.core.exception.FileAccessErrorCode;
import com.amoylabs.sfe4j.core.exception.FileAccessException;
import com.amoylabs.sfe4j.core.service.FileExplorerService;
import com.amoylabs.sfe4j.core.text.TextCharsets;
import com.amoylabs.sfe4j.core.text.TextReadMode;
import com.amoylabs.sfe4j.core.text.TextReadRequest;
import com.amoylabs.sfe4j.core.text.TextReadResult;
import com.amoylabs.sfe4j.core.vo.TextPreviewVO;
import com.amoylabs.sfe4j.spring.boot.access.DirectoryAuthorizationService;
import com.amoylabs.sfe4j.spring.boot.web.LinkBuilder;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

/**
 * Plain text page of one file.
 *
 * <p>The content is bounded, rendered inside a {@code <pre>} block with HTML escaping enabled in the
 * template, and served with {@code X-Content-Type-Options: nosniff}: the file content never takes part
 * in script, style or template interpretation.</p>
 *
 * <p>With {@code sfe4j.logs.enabled=true} the page accepts a read mode, a number of lines and an
 * encoding, and offers following the file through the JSON API. Without it the page stays the bounded
 * head preview of the previous releases.</p>
 */
@Controller
public class FileViewerController {

    private FileExplorerService fileExplorerService;

    private DirectoryAuthorizationService authorizationService;

    private Sfe4jConfiguration configuration;

    private ServletContext servletContext;

    @GetMapping("/file-viewer")
    public ModelAndView exec(@RequestParam("file") String filePath,
                             @RequestParam(value = "mode", required = false) String mode,
                             @RequestParam(value = "lines", required = false) Integer lines,
                             @RequestParam(value = "charset", required = false) String charset,
                             HttpSession session, HttpServletResponse response) {
        if (filePath.isBlank()) {
            throw new FileAccessException(FileAccessErrorCode.INVALID_REQUEST, "The 'file' parameter is required.");
        }

        FileAccessContext context = authorizationService.contextFor(session);
        boolean logsEnabled = configuration != null && configuration.getLogsEnabled();

        TextPreviewVO preview = logsEnabled
                ? view(filePath, mode, lines, charset, context)
                : fileExplorerService.preview(filePath, context);

        response.setHeader("X-Content-Type-Options", "nosniff");

        ModelAndView modelAndView = new ModelAndView("file-viewer");
        modelAndView.addObject("ctx", servletContext.getContextPath());
        modelAndView.addObject("url", new LinkBuilder(servletContext.getContextPath()));
        modelAndView.addObject("meta", fileExplorerService.buildMetaInfo());
        modelAndView.addObject("preview", preview);
        modelAndView.addObject("logsEnabled", logsEnabled);
        modelAndView.addObject("charsets", TextCharsets.supportedNames());
        modelAndView.addObject("defaultLines", logsEnabled ? configuration.getLogsDefaultLines() : 0);
        modelAndView.addObject("maxLines", logsEnabled ? configuration.getLogsMaxLines() : 0);
        return modelAndView;
    }

    /**
     * Bounded read through the text read service, mapped onto the page model.
     */
    private TextPreviewVO view(String filePath, String mode, Integer lines, String charset,
                               FileAccessContext context) {
        TextReadMode readMode;
        try {
            readMode = TextReadMode.from(mode == null ? TextReadMode.HEAD.name() : mode);
        } catch (IllegalArgumentException e) {
            throw new FileAccessException(FileAccessErrorCode.INVALID_REQUEST,
                    "The 'mode' parameter must be 'head' or 'tail'.");
        }

        TextReadRequest request = new TextReadRequest();
        request.setMode(readMode);
        request.setCharset(charset);
        request.setLines(lines == null
                ? (readMode == TextReadMode.TAIL ? configuration.getLogsDefaultLines() : TextReadRequest.NO_LINE_LIMIT)
                : lines);
        request.setMaxLines(configuration.getLogsMaxLines());
        request.setMaxBytes(configuration.getPreviewMaxBytes());
        request.setMaxScanBytes(configuration.getLogsMaxScanBytes());

        TextReadResult result = fileExplorerService.readText(filePath, context, request);

        String normalized = filePath.replace('\\', '/');
        int separator = normalized.lastIndexOf('/');
        String name = separator >= 0 ? normalized.substring(separator + 1) : normalized;

        TextPreviewVO preview = new TextPreviewVO();
        preview.setFileName(name.isEmpty() ? normalized : name);
        preview.setPath(filePath);
        preview.setContent(result.getText());
        preview.setSize(result.getFileSize());
        preview.setMaxBytes(configuration.getPreviewMaxBytes());
        preview.setTruncated(result.isTruncated());
        preview.setLineTruncated(result.isLineTruncated());
        preview.setDecodingIssue(result.isDecodingIssue());
        preview.setEncoding(result.getCharset());
        preview.setMode(readMode.name().toLowerCase(java.util.Locale.ROOT));
        preview.setLines(request.getLines() < 0 ? 0 : request.getLines());
        preview.setMaxLines(configuration.getLogsMaxLines());
        preview.setCursor(result.getCursor());
        preview.setGeneration(result.getGeneration());
        preview.setParentPath(separator > 0 ? normalized.substring(0, separator) : null);
        return preview;
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
    public void setConfiguration(Sfe4jConfiguration configuration) {
        this.configuration = configuration;
    }

    @Autowired
    public void setServletContext(ServletContext servletContext) {
        this.servletContext = servletContext;
    }
}
