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

    private String encoding;

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

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }
}
