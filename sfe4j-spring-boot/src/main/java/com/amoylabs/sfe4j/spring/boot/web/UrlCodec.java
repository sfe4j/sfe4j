package com.amoylabs.sfe4j.spring.boot.web;

import java.nio.charset.StandardCharsets;

/**
 * Percent encoding for query parameter values.
 *
 * <p>Everything except the unreserved characters is encoded, including {@code %}, {@code &} and
 * {@code +}. The value therefore always survives the round trip through the Servlet container, even for
 * file names containing plus signs, ampersands or percent escapes.</p>
 */
public final class UrlCodec {

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private UrlCodec() {
    }

    public static String encodeQueryValue(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder encoded = new StringBuilder(value.length() * 3);
        for (byte raw : value.getBytes(StandardCharsets.UTF_8)) {
            int character = raw & 0xFF;
            if (isUnreserved(character)) {
                encoded.append((char) character);
            } else {
                encoded.append('%').append(HEX[character >> 4]).append(HEX[character & 0x0F]);
            }
        }
        return encoded.toString();
    }

    private static boolean isUnreserved(int character) {
        return (character >= 'a' && character <= 'z')
                || (character >= 'A' && character <= 'Z')
                || (character >= '0' && character <= '9')
                || character == '-' || character == '_' || character == '.' || character == '~';
    }
}
