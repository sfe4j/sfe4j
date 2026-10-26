package com.amoylabs.sfe4j.core.exception;

/**
 * Stable error contract between the core and the web layer.
 *
 * <p>The HTTP status is part of the contract documented in {@code docs/design/p0-security-reliability.md};
 * messages returned to the client never contain the original exception text.</p>
 */
public enum FileAccessErrorCode {

    /**
     * Missing parameter, illegal path or illegal encoding.
     */
    INVALID_REQUEST(400),

    /**
     * A directory password has not been provided yet.
     */
    DIRECTORY_AUTH_REQUIRED(403),

    /**
     * Outside of the accessible scope, policy rejection or unreadable target.
     */
    ACCESS_DENIED(403),

    /**
     * The target does not exist inside the accessible scope.
     */
    FILE_NOT_FOUND(404),

    /**
     * The target cannot be previewed (binary content, directory, pipe or device).
     */
    UNSUPPORTED_PREVIEW(415),

    /**
     * Password attempts or concurrent transfers exceeded the configured limit.
     */
    RATE_LIMITED(429),

    /**
     * Unexpected I/O failure.
     */
    FILE_IO_ERROR(500);

    private final int httpStatus;

    FileAccessErrorCode(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
