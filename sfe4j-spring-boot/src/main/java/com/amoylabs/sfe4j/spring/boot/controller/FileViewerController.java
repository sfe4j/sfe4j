package com.amoylabs.sfe4j.spring.boot.controller;

import com.amoylabs.sfe4j.core.access.FileAccessContext;
import com.amoylabs.sfe4j.core.exception.FileAccessErrorCode;
import com.amoylabs.sfe4j.core.exception.FileAccessException;
import com.amoylabs.sfe4j.core.service.FileExplorerService;
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
 * Plain text preview.
 *
 * <p>The content is bounded by {@code sfe4j.preview.max-bytes}, rendered inside a {@code <pre>} block with
 * HTML escaping enabled in the template, and served with {@code X-Content-Type-Options: nosniff}: the file
 * content never takes part in script, style or template interpretation.</p>
 */
@Controller
public class FileViewerController {

    private FileExplorerService fileExplorerService;

    private DirectoryAuthorizationService authorizationService;

    private ServletContext servletContext;

    @GetMapping("/file-viewer")
    public ModelAndView exec(@RequestParam("file") String filePath, HttpSession session, HttpServletResponse response) {
        if (filePath.isBlank()) {
            throw new FileAccessException(FileAccessErrorCode.INVALID_REQUEST, "The 'file' parameter is required.");
        }

        FileAccessContext context = authorizationService.contextFor(session);
        TextPreviewVO preview = fileExplorerService.preview(filePath, context);

        response.setHeader("X-Content-Type-Options", "nosniff");

        ModelAndView modelAndView = new ModelAndView("file-viewer");
        modelAndView.addObject("ctx", servletContext.getContextPath());
        modelAndView.addObject("url", new LinkBuilder(servletContext.getContextPath()));
        modelAndView.addObject("meta", fileExplorerService.buildMetaInfo());
        modelAndView.addObject("preview", preview);
        return modelAndView;
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
    public void setServletContext(ServletContext servletContext) {
        this.servletContext = servletContext;
    }
}
