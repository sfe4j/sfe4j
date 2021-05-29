package com.amoylabs.sfe4j.spring.boot.controller;

import com.amoylabs.sfe4j.spring.boot.service.FileExplorerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.ServletContext;

@Controller
public class FileExplorerController {

    private ServletContext servletContext;
    private FileExplorerService fileExplorerService;

    @GetMapping("/file-explorer")
    public ModelAndView files(@RequestParam("dir") @Nullable String dir) {

        ModelAndView mv = new ModelAndView("file-explorer");
        mv.addObject("ctx", servletContext.getContextPath());
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
}
