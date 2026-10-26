package com.amoylabs.sfe4j.core.vo;

import java.nio.file.Path;

/**
 * Download target handed to the web layer.
 *
 * <p>The core never opens the file for a download: the caller streams it so the resource stays open
 * for exactly the lifetime of the response.</p>
 */
public class FileDownloadVO {

    private final Path path;

    private final String fileName;

    private final long length;

    public FileDownloadVO(Path path, String fileName, long length) {
        this.path = path;
        this.fileName = fileName;
        this.length = length;
    }

    /**
     * Real path of the file to stream.
     */
    public Path getPath() {
        return path;
    }

    /**
     * Base name of the file, used to build {@code Content-Disposition}.
     */
    public String getFileName() {
        return fileName;
    }

    public long getLength() {
        return length;
    }
}
