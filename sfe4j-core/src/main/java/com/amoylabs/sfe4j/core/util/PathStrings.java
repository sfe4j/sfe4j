package com.amoylabs.sfe4j.core.util;

import java.nio.file.Path;

/**
 * Path to string conversions shared by the pages.
 *
 * <p>Separators are normalized only on Windows. On POSIX systems a backslash is a legal file name
 * character and must not be rewritten into a directory separator.</p>
 */
public final class PathStrings {

    private static final boolean IS_WINDOWS =
            System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("windows");

    private PathStrings() {
    }

    /**
     * @return the path as used in links, with forward slashes on Windows
     */
    public static String toUrlPath(Path path) {
        if (path == null) {
            return "";
        }
        String value = path.toString();
        return IS_WINDOWS ? value.replace('\\', '/') : value;
    }

    /**
     * @return the last segment of the path, or the whole path for a filesystem root
     */
    public static String displayName(Path path) {
        if (path == null) {
            return "";
        }
        Path fileName = path.getFileName();
        return fileName == null ? toUrlPath(path) : fileName.toString();
    }

    /**
     * Removes control characters so a name can never inject a header break.
     */
    public static String sanitizeHeaderValue(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            builder.append(c < 0x20 || c == 0x7F ? '_' : c);
        }
        return builder.toString();
    }
}
