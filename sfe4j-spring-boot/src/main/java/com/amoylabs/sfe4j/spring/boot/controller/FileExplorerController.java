package com.amoylabs.sfe4j.spring.boot.controller;

import com.amoylabs.sfe4j.core.service.FileExplorerService;
import com.amoylabs.sfe4j.core.configuration.Sfe4jConfiguration;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpSession;
import java.util.Map;

@Controller
public class FileExplorerController {

    private ServletContext servletContext;
    private FileExplorerService fileExplorerService;
    private Sfe4jConfiguration sfe4jConfiguration;

    @GetMapping("/file-explorer")
    public ModelAndView exec(@RequestParam("dir") @Nullable String dir, HttpSession session) {
        return handleRequest(dir, null, session);
    }

    @PostMapping("/file-explorer")
    public ModelAndView execPost(@RequestParam("dir") @Nullable String dir, @RequestParam("pwd") @Nullable String pwd, HttpSession session) {
        return handleRequest(dir, pwd, session);
    }

    private ModelAndView handleRequest(String dir, String pwd, HttpSession session) {
        String targetDir = StringUtils.isNotEmpty(dir) ? dir : sfe4jConfiguration.getBaseDirPath();
        if (targetDir == null) targetDir = "";

        Map<String, String> passwords = sfe4jConfiguration.getFolderPasswords();
        if (passwords != null) {
            String protectedPath = null;
            String requiredPwd = null;

            for (Map.Entry<String, String> entry : passwords.entrySet()) {
                String pPath = entry.getKey();
                if (targetDir.equals(pPath) || targetDir.startsWith(pPath + "/") || targetDir.startsWith(pPath + "\\")) {
                    if (protectedPath == null || pPath.length() > protectedPath.length()) {
                        protectedPath = pPath;
                        requiredPwd = entry.getValue();
                    }
                }
            }

            if (protectedPath != null) {
                String sessionKey = "auth_" + protectedPath;
                Boolean isAuth = (Boolean) session.getAttribute(sessionKey);
                if (isAuth == null || !isAuth) {
                    if (pwd != null && pwd.equals(requiredPwd)) {
                        session.setAttribute(sessionKey, true);
                    } else {
                        ModelAndView mv = new ModelAndView("file-explorer");
                        mv.addObject("ctx", servletContext.getContextPath());
                        mv.addObject("meta", fileExplorerService.buildMetaInfo());
                        mv.addObject("requiresPassword", true);
                        mv.addObject("targetDir", targetDir);
                        if (pwd != null) {
                            mv.addObject("pwdError", true);
                        }
                        return mv;
                    }
                }
            }
        }

        ModelAndView mv = new ModelAndView("file-explorer");
        mv.addObject("ctx", servletContext.getContextPath());
        mv.addObject("meta", fileExplorerService.buildMetaInfo());
        mv.addObject("fileTree", fileExplorerService.buildFileTree(dir));
        return mv;
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
    public void setSfe4jConfiguration(Sfe4jConfiguration sfe4jConfiguration) {
        this.sfe4jConfiguration = sfe4jConfiguration;
    }
}
