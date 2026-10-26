package com.amoylabs.sfe4j.core.vo;

/**
 * One level of the breadcrumb of the currently listed directory.
 *
 * <p>Segments are accumulated by the core so the page never recomputes prefixes from strings: a
 * directory whose name appears several times in the path must not produce duplicated links.</p>
 */
public class BreadcrumbVO {

    private final String name;

    private final String path;

    private final boolean current;

    public BreadcrumbVO(String name, String path, boolean current) {
        this.name = name;
        this.path = path;
        this.current = current;
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
    }

    public boolean isCurrent() {
        return current;
    }
}
