package com.amoylabs.sfe4j.spring.boot.web;

/**
 * Builds user visible labels from a raw path parameter.
 *
 * <p>Only the last segment is shown on the password challenge page, so an unauthenticated caller never
 * learns the absolute path of the host directory layout.</p>
 */
public final class PathLabels {

    private PathLabels() {
    }

    public static String displayLabel(String rawPath) {
        if (rawPath == null || rawPath.isEmpty()) {
            return "/";
        }
        int end = rawPath.length();
        while (end > 1 && (rawPath.charAt(end - 1) == '/' || rawPath.charAt(end - 1) == '\\')) {
            end--;
        }
        String trimmed = rawPath.substring(0, end);
        int separator = Math.max(trimmed.lastIndexOf('/'), trimmed.lastIndexOf('\\'));
        if (separator < 0) {
            return trimmed;
        }
        if (separator == end - 1) {
            return trimmed;
        }
        return trimmed.substring(separator + 1);
    }
}
