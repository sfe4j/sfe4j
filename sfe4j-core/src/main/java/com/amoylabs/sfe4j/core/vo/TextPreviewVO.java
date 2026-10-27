package com.amoylabs.sfe4j.core.vo;

/**
 * Bounded plain text preview of a file.
 */
public class TextPreviewVO {

    private String fileName;

    private String path;

    private String parentPath;

    private String content;

    private long size;

    private long maxBytes;

    private boolean truncated;

    private boolean decodingIssue;

    private boolean lineTruncated;

    private String encoding;

    private String mode = "head";

    private int lines;

    private int maxLines;

    private String cursor;

    private String generation;

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    /**
     * Lexical path of the file, used to build the return link.
     */
    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    /**
     * Lexical path of the parent directory, used to build the return link.
     */
    public String getParentPath() {
        return parentPath;
    }

    public void setParentPath(String parentPath) {
        this.parentPath = parentPath;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public long getMaxBytes() {
        return maxBytes;
    }

    public void setMaxBytes(long maxBytes) {
        this.maxBytes = maxBytes;
    }

    /**
     * {@code true} when the file is larger than {@code sfe4j.preview.max-bytes} and only the head was
     * read.
     */
    public boolean isTruncated() {
        return truncated;
    }

    public void setTruncated(boolean truncated) {
        this.truncated = truncated;
    }

    /**
     * {@code true} when the bytes could not be decoded with the selected charset and replacement
     * characters were used.
     */
    public boolean isDecodingIssue() {
        return decodingIssue;
    }

    public void setDecodingIssue(boolean decodingIssue) {
        this.decodingIssue = decodingIssue;
    }

    /**
     * {@code true} when the text ends inside a line: a single line exceeded the byte limit.
     */
    public boolean isLineTruncated() {
        return lineTruncated;
    }

    public void setLineTruncated(boolean lineTruncated) {
        this.lineTruncated = lineTruncated;
    }

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    /**
     * @return read mode of the page: {@code head} or {@code tail}
     */
    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    /**
     * @return number of lines that were requested, {@code 0} when the read is bounded by bytes only
     */
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

    /**
     * @return opaque resume token of the read, {@code null} when log following is disabled
     */
    public String getCursor() {
        return cursor;
    }

    public void setCursor(String cursor) {
        this.cursor = cursor;
    }

    /**
     * @return identity of the file, used to detect rotation while following
     */
    public String getGeneration() {
        return generation;
    }

    public void setGeneration(String generation) {
        this.generation = generation;
    }
}
