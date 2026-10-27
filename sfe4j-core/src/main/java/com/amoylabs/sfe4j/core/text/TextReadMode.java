package com.amoylabs.sfe4j.core.text;

/**
 * Which end of a file a bounded text read starts from.
 */
public enum TextReadMode {

    /**
     * Reads forward from the beginning of the file, or from an explicit byte offset.
     */
    HEAD,

    /**
     * Reads the last lines of the file, scanning backwards within {@code sfe4j.logs.max-scan-bytes}.
     */
    TAIL;

    /**
     * @param value request parameter value, {@code null} or blank means {@link #TAIL}
     * @throws IllegalArgumentException when the value is not a known mode
     */
    public static TextReadMode from(String value) {
        if (value == null || value.isBlank()) {
            return TAIL;
        }
        return TextReadMode.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
    }
}
