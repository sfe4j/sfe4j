package com.amoylabs.sfe4j.core.text;

/**
 * Outcome of one bounded text read.
 *
 * <p>The positions are byte offsets into the file as it was when the read started; they are the only
 * state a caller needs to continue, and they are meaningless without a new authorization.</p>
 */
public class TextReadResult {

    private String text = "";

    private String charset = TextCharsets.UTF_8;

    private boolean byteOrderMark;

    private int lines;

    private boolean truncated;

    private boolean lineTruncated;

    private boolean decodingIssue;

    private long startPosition;

    private long nextPosition;

    private long fileSize;

    private boolean endOfFile = true;

    private String generation;

    private boolean reset;

    private String resetReason;

    /**
     * Opaque resume token issued by the calling layer; {@code core} only carries it through.
     */
    private String cursor;

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text == null ? "" : text;
    }

    /**
     * @return the canonical name of the encoding the text was decoded with
     */
    public String getCharset() {
        return charset;
    }

    public void setCharset(String charset) {
        this.charset = charset;
    }

    /**
     * @return {@code true} when the effective encoding came from a byte order mark
     */
    public boolean isByteOrderMark() {
        return byteOrderMark;
    }

    public void setByteOrderMark(boolean byteOrderMark) {
        this.byteOrderMark = byteOrderMark;
    }

    /**
     * @return number of complete or partial lines contained in {@link #getText()}
     */
    public int getLines() {
        return lines;
    }

    public void setLines(int lines) {
        this.lines = lines;
    }

    /**
     * @return {@code true} when the read stopped at a byte limit and the file continues
     */
    public boolean isTruncated() {
        return truncated;
    }

    public void setTruncated(boolean truncated) {
        this.truncated = truncated;
    }

    /**
     * @return {@code true} when the text ends inside a line because a single line exceeded the byte
     *         limit; the page tells the user that the line is not shown completely
     */
    public boolean isLineTruncated() {
        return lineTruncated;
    }

    public void setLineTruncated(boolean lineTruncated) {
        this.lineTruncated = lineTruncated;
    }

    /**
     * @return {@code true} when bytes could not be decoded and replacement characters were used
     */
    public boolean isDecodingIssue() {
        return decodingIssue;
    }

    public void setDecodingIssue(boolean decodingIssue) {
        this.decodingIssue = decodingIssue;
    }

    public long getStartPosition() {
        return startPosition;
    }

    public void setStartPosition(long startPosition) {
        this.startPosition = startPosition;
    }

    /**
     * @return byte offset the next read continues from
     */
    public long getNextPosition() {
        return nextPosition;
    }

    public void setNextPosition(long nextPosition) {
        this.nextPosition = nextPosition;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    /**
     * @return {@code true} when {@link #getNextPosition()} reached the end of the file
     */
    public boolean isEndOfFile() {
        return endOfFile;
    }

    public void setEndOfFile(boolean endOfFile) {
        this.endOfFile = endOfFile;
    }

    /**
     * @return identity of the file, used to detect rotation or replacement
     */
    public String getGeneration() {
        return generation;
    }

    public void setGeneration(String generation) {
        this.generation = generation;
    }

    /**
     * @return {@code true} when the file was replaced or shrank and the previous cursor is invalid
     */
    public boolean isReset() {
        return reset;
    }

    public void setReset(boolean reset) {
        this.reset = reset;
    }

    /**
     * @return short reason of a reset: {@code generation-changed} or {@code truncated}
     */
    public String getResetReason() {
        return resetReason;
    }

    public void setResetReason(String resetReason) {
        this.resetReason = resetReason;
    }

    public String getCursor() {
        return cursor;
    }

    public void setCursor(String cursor) {
        this.cursor = cursor;
    }
}
