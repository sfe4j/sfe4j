package com.amoylabs.sfe4j.spring.boot.controller;

import com.amoylabs.sfe4j.core.service.FileExplorerService;
import com.amoylabs.sfe4j.core.vo.FileContentVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class FileDownloaderController {

    private FileExplorerService fileExplorerService;

    @GetMapping("/file-downloader")
    public ResponseEntity exec(@RequestParam("file") @Nullable String filePath) {
        FileContentVO fileContentVO = fileExplorerService.readFile(filePath);

        return ResponseEntity
                .status(200)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(fileContentVO.getContent().length)
                .header("Content-Disposition", "attachment; filename=" + fileContentVO.getFileName())
                .body(fileContentVO.getContent());
    }

    @Autowired
    public void setFileExplorerService(FileExplorerService fileExplorerService) {
        this.fileExplorerService = fileExplorerService;
    }
}
