package com.amoylabs.sfe4j.spring.boot.controller;

import com.amoylabs.sfe4j.core.access.FileAccessContext;
import com.amoylabs.sfe4j.core.exception.FileAccessErrorCode;
import com.amoylabs.sfe4j.core.exception.FileAccessException;
import com.amoylabs.sfe4j.core.service.FileExplorerService;
import com.amoylabs.sfe4j.core.vo.FileDownloadVO;
import com.amoylabs.sfe4j.spring.boot.access.DirectoryAuthorizationService;
import com.amoylabs.sfe4j.spring.boot.web.DownloadConcurrencyLimiter;
import com.amoylabs.sfe4j.spring.boot.web.PermitReleasingInputStream;

import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Streamed file download.
 *
 * <p>The file is never loaded into memory: a fixed size buffer is copied to the response and the stream is
 * closed by the framework once the body has been written, or when the client aborts. The concurrency
 * limiter rejects a request with 429 instead of queueing on a Servlet thread.</p>
 */
@Controller
public class FileDownloaderController {

    private FileExplorerService fileExplorerService;

    private DirectoryAuthorizationService authorizationService;

    private DownloadConcurrencyLimiter concurrencyLimiter;

    @GetMapping("/file-downloader")
    public ResponseEntity<Resource> exec(@RequestParam("file") String filePath, HttpSession session) {
        if (filePath.isBlank()) {
            throw new FileAccessException(FileAccessErrorCode.INVALID_REQUEST, "The 'file' parameter is required.");
        }

        FileAccessContext context = authorizationService.contextFor(session);
        FileDownloadVO target = fileExplorerService.openDownload(filePath, context);

        if (!concurrencyLimiter.tryAcquire()) {
            throw new FileAccessException(FileAccessErrorCode.RATE_LIMITED,
                    "Too many concurrent downloads. Please retry later.");
        }

        InputStream inputStream;
        try {
            inputStream = Files.newInputStream(target.getPath());
        } catch (IOException e) {
            concurrencyLimiter.release();
            throw new FileAccessException(FileAccessErrorCode.FILE_IO_ERROR,
                    "The requested file cannot be opened for download.", e);
        }

        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(target.getFileName(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(target.getLength())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(new PermitReleasingInputStream(inputStream, concurrencyLimiter::release)));
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
    public void setConcurrencyLimiter(DownloadConcurrencyLimiter concurrencyLimiter) {
        this.concurrencyLimiter = concurrencyLimiter;
    }
}
