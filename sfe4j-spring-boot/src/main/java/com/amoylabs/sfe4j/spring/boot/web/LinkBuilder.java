package com.amoylabs.sfe4j.spring.boot.web;

/**
 * Builds the internal links of the pages.
 *
 * <p>Every link is generated on the server from the configured context path and an encoded parameter; the
 * templates never concatenate a raw path into a URL. This keeps non root context paths working and avoids
 * breaking links for file names with spaces, {@code #}, {@code &} or non ASCII characters.</p>
 */
public class LinkBuilder {

    private final String contextPath;

    public LinkBuilder(String contextPath) {
        this.contextPath = contextPath == null ? "" : contextPath;
    }

    public String entry() {
        return contextPath + "/file-explorer";
    }

    public String listing(String dir) {
        return withParameter(entry(), "dir", dir);
    }

    public String preview(String file) {
        return withParameter(contextPath + "/file-viewer", "file", file);
    }

    public String download(String file) {
        return withParameter(contextPath + "/file-downloader", "file", file);
    }

    public String logout(String dir) {
        return withParameter(contextPath + "/file-explorer/logout", "dir", dir);
    }

    private String withParameter(String base, String name, String value) {
        if (value == null || value.isEmpty()) {
            return base;
        }
        return base + "?" + name + "=" + UrlCodec.encodeQueryValue(value);
    }
}
