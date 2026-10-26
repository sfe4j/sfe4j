package com.amoylabs.sfe4j.core.access;

/**
 * Operation performed on a filesystem target.
 *
 * <p>Every operation must pass the same authorization facade
 * ({@link FileAccessService}); no caller is allowed to read a file directly.</p>
 */
public enum FileOperation {

    /**
     * List the content of a directory.
     */
    LIST,

    /**
     * Read a bounded, plain text representation of a regular file.
     */
    PREVIEW,

    /**
     * Stream the raw bytes of a regular file.
     */
    DOWNLOAD
}
