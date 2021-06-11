package com.amoylabs.sfe4j.spring.boot.controller;

import com.amoylabs.sfe4j.core.service.FileExplorerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

@Controller
public class FileViewerController {

    private FileExplorerService fileExplorerService;

    @GetMapping("/file-viewer")
    public ModelAndView exec(@RequestParam("file") @Nullable String filePath) {
        ModelAndView mv = new ModelAndView("file-viewer");
        mv.addObject("fileContent", new String(fileExplorerService.readFile(filePath).getContent()));
        return mv;
    }

    @Autowired
    public void setFileExplorerService(FileExplorerService fileExplorerService) {
        this.fileExplorerService = fileExplorerService;
    }
}
