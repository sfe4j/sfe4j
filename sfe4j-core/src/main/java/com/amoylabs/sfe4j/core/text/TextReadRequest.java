package com.amoylabs.sfe4j.core.text;

import java.nio.file.Path;

/**
 * Parameters of one bounded text read.
 *
 * <p>Every offset in this request and in {@link TextReadResult} is a <em>byte</em> offset, never a
 * character count: a resume position has to stay valid when the file grows with multi byte content.</p>
 *
 * <p>The path is set by the access facade after the request has been authorized; a caller must never
 * pass a value that did not come from {@code FileAccessService}.</p>
 */
public class TextReadRequest {

    /**
     * Number of lines read when the caller does not ask for a specific amount.
     */
    public static final int DEFAULT_LINES = 200;

    /**
     * Value of {@link #lines} that disables the line limit: a read is then bounded by bytes only.
     */
    public static final int NO_LINE_LIMIT = -1;

    /**
     * Upper bound of {@link #lines} when the caller does not configure one.
     */
    public static final int DEFAULT_MAX_LINES = 2000;

    /**
     * Upper bound of the bytes decoded in one pass when the caller does not configure one.
     */
    public static final long DEFAULT_MAX_BYTES = 1048576L;

    /**
     * Upper bound of the bytes scanned backwards in {@link TextReadMode#TAIL} when the caller does not
     * configure one.
     */
    public static final long DEFAULT_MAX_SCAN_BYTES = 1048576L;

    private Path realPath;

    private TextReadMode mode = TextReadMode.TAIL;

    private int lines = DEFAULT_LINES;

    private int maxLines = DEFAULT_MAX_LINES;

    private String charset;

    private long startPosition;

    private String expectedGeneration;

    private long maxBytes = DEFAULT_MAX_BYTES;

    private long maxScanBytes = DEFAULT_MAX_SCAN_BYTES;

    public Path getRealPath() {
        return realPath;
    }

    public void setRealPath(Path realPath) {
        this.realPath = realPath;
    }

    public TextReadMode getMode() {
        return mode;
    }

    public void setMode(TextReadMode mode) {
        this.mode = mode == null ? TextReadMode.TAIL : mode;
    }

    public int getLines() {
        return lines;
    }

    public void setLines(int lines) {
        this.lines = lines;
    }

    public int getMaxLines() {
        return maxLines;
    }

    public void setMaxLines(int maxLines) {
        this.maxLines = maxLines;
    }

    public String getCharset() {
        return charset;
    }

    public void setCharset(String charset) {
        this.charset = charset;
    }

    public long getStartPosition() {
        return startPosition;
    }

    public void setStartPosition(long startPosition) {
        this.startPosition = startPosition;
    }

    /**
     * Generation of the file the caller read before; a mismatch means the file was replaced and the
     * cursor is not valid any more.
     */
    public String getExpectedGeneration() {
        return expectedGeneration;
    }

    public void setExpectedGeneration(String expectedGeneration) {
        this.expectedGeneration = expectedGeneration;
    }

    public long getMaxBytes() {
        return maxBytes;
    }

    public void setMaxBytes(long maxBytes) {
        this.maxBytes = maxBytes;
    }

    public long getMaxScanBytes() {
        return maxScanBytes;
    }

    public void setMaxScanBytes(long maxScanBytes) {
        this.maxScanBytes = maxScanBytes;
    }

    /**
     * @return the requested number of lines, clamped to {@code [1, maxLines]}, or
     *         {@link Integer#MAX_VALUE} when the read is not limited by lines
     */
    public int effectiveLines() {
        if (lines < 0) {
            return Integer.MAX_VALUE;
        }
        int requested = lines == 0 ? DEFAULT_LINES : lines;
        int upper = maxLines <= 0 ? DEFAULT_MAX_LINES : maxLines;
        return Math.min(requested, upper);
    }
}
