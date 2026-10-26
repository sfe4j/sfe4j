package com.amoylabs.sfe4j.spring.boot.controller;

import com.amoylabs.sfe4j.core.access.FileAccessContext;
import com.amoylabs.sfe4j.core.exception.FileAccessErrorCode;
import com.amoylabs.sfe4j.core.exception.FileAccessException;
import com.amoylabs.sfe4j.core.service.FileExplorerService;
import com.amoylabs.sfe4j.core.vo.FileTreeVO;
import com.amoylabs.sfe4j.spring.boot.access.CsrfSupport;
import com.amoylabs.sfe4j.spring.boot.access.DirectoryAuthorizationService;
import com.amoylabs.sfe4j.spring.boot.web.LinkBuilder;
import com.amoylabs.sfe4j.spring.boot.web.PathLabels;
import com.amoylabs.sfe4j.spring.boot.web.UrlCodec;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

/**
 * Directory listing and directory password challenge.
 *
 * <p>The listing never decides on its own whether a password is required: the request goes through the
 * access facade and a {@code DIRECTORY_AUTH_REQUIRED} rejection is turned into the challenge page.</p>
 */
@Controller
public class FileExplorerController {

    private static final String LIST_VIEW = "file-explorer";

    private static final String ENTRY_POINT = "/file-explorer";

    private ServletContext servletContext;

    private FileExplorerService fileExplorerService;

    private DirectoryAuthorizationService authorizationService;

    private CsrfSupport csrfSupport;

    @GetMapping(ENTRY_POINT)
    public ModelAndView exec(@RequestParam(value = "dir", required = false) @Nullable String dir,
                             HttpSession session, HttpServletRequest request) {
        FileAccessContext context = authorizationService.contextFor(session);
        try {
            FileTreeVO fileTree = fileExplorerService.buildFileTree(dir, context);
            ModelAndView modelAndView = new ModelAndView(LIST_VIEW);
            modelAndView.addObject("ctx", servletContext.getContextPath());
            modelAndView.addObject("url", new LinkBuilder(servletContext.getContextPath()));
            modelAndView.addObject("meta", fileExplorerService.buildMetaInfo());
            modelAndView.addObject("fileTree", fileTree);
            modelAndView.addObject("csrfParameter", CsrfSupport.PARAMETER_NAME);
            modelAndView.addObject("csrfToken", csrfSupport.token(request));
            modelAndView.addObject("showLogout", !authorizationService.requiredRules(dir).isEmpty());
            modelAndView.addObject("currentDir", dir == null ? "" : dir);
            return modelAndView;
        } catch (FileAccessException exception) {
            if (exception.getCode() != FileAccessErrorCode.DIRECTORY_AUTH_REQUIRED) {
                throw exception;
            }
            return challenge(dir, request, false);
        }
    }

    @PostMapping(ENTRY_POINT)
    public ModelAndView execPost(@RequestParam(value = "dir", required = false) @Nullable String dir,
                                 @RequestParam(value = "pwd", required = false) @Nullable String pwd,
                                 HttpSession session, HttpServletRequest request) {
        csrfSupport.verify(request);

        boolean granted = authorizationService.authenticate(request, dir, pwd);
        if (!granted) {
            return challenge(dir, request, true);
        }
        csrfSupport.rotate(request);
        return new ModelAndView("redirect:" + redirectTarget(dir));
    }

    @PostMapping(ENTRY_POINT + "/logout")
    public ModelAndView logout(@RequestParam(value = "dir", required = false) @Nullable String dir,
                               HttpServletRequest request) {
        csrfSupport.verify(request);
        authorizationService.revoke(request);
        return new ModelAndView("redirect:" + redirectTarget(dir));
    }

    private ModelAndView challenge(String dir, HttpServletRequest request, boolean passwordError) {
        ModelAndView modelAndView = new ModelAndView(LIST_VIEW);
        modelAndView.addObject("ctx", servletContext.getContextPath());
        modelAndView.addObject("url", new LinkBuilder(servletContext.getContextPath()));
        modelAndView.addObject("meta", fileExplorerService.buildMetaInfo());
        modelAndView.addObject("requiresPassword", true);
        modelAndView.addObject("targetDir", dir == null ? "" : dir);
        modelAndView.addObject("targetLabel", PathLabels.displayLabel(dir));
        modelAndView.addObject("pwdError", passwordError);
        modelAndView.addObject("csrfParameter", CsrfSupport.PARAMETER_NAME);
        modelAndView.addObject("csrfToken", csrfSupport.token(request));
        return modelAndView;
    }

    /**
     * Builds the redirect target on the server. The path stays inside the application and the parameter
     * is encoded, so the value can never become an open redirect.
     */
    private String redirectTarget(@Nullable String dir) {
        if (dir == null || dir.isEmpty()) {
            return ENTRY_POINT;
        }
        return ENTRY_POINT + "?dir=" + UrlCodec.encodeQueryValue(dir);
    }

    @Autowired
    public void setServletContext(ServletContext servletContext) {
        this.servletContext = servletContext;
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
    public void setCsrfSupport(CsrfSupport csrfSupport) {
        this.csrfSupport = csrfSupport;
    }
}
