package com.amoylabs.sfe4j.core.exception;

/**
 * Thrown when a file access request must be rejected.
 *
 * <p>The message is safe to show to the caller: it must not contain host paths, stack traces,
 * passwords or file content. The original exception, when any, is kept as the cause for logging
 * only and must not be serialized into a response.</p>
 */
public class FileAccessException extends Sfe4jRuntimeException {

    private static final long serialVersionUID = -3108454576113604991L;

    private final FileAccessErrorCode code;

    public FileAccessException(FileAccessErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public FileAccessException(FileAccessErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public FileAccessErrorCode getCode() {
        return code;
    }
}
