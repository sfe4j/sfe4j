package com.amoylabs.sfe4j.core.text;

import com.amoylabs.sfe4j.core.exception.FileAccessErrorCode;
import com.amoylabs.sfe4j.core.exception.FileAccessException;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The encodings a text read accepts, and the byte order mark handling.
 *
 * <p>Only UTF-8, UTF-16LE, UTF-16BE and GB18030 are accepted. A read never guesses an arbitrary
 * encoding from the content, because a wrong guess silently produces mojibake instead of an error the
 * user can correct.</p>
 */
public final class TextCharsets {

    /**
     * Canonical name of the default encoding.
     */
    public static final String UTF_8 = "UTF-8";

    public static final String UTF_16LE = "UTF-16LE";

    public static final String UTF_16BE = "UTF-16BE";

    public static final String GB18030 = "GB18030";

    /**
     * Byte sequence of the UTF-8 byte order mark.
     */
    public static final byte[] UTF_8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private static final Map<String, Charset> SUPPORTED = new LinkedHashMap<>();

    static {
        SUPPORTED.put(UTF_8, StandardCharsets.UTF_8);
        SUPPORTED.put(UTF_16LE, StandardCharsets.UTF_16LE);
        SUPPORTED.put(UTF_16BE, StandardCharsets.UTF_16BE);
        SUPPORTED.put(GB18030, Charset.forName(GB18030));
    }

    private TextCharsets() {
    }

    /**
     * @return the canonical names accepted in a request, in display order
     */
    public static List<String> supportedNames() {
        return List.copyOf(SUPPORTED.keySet());
    }

    /**
     * Resolves a requested encoding.
     *
     * @param name canonical name, {@code null} or blank means "detect from the byte order mark"
     * @return the matching charset, {@code null} when no encoding was requested
     * @throws FileAccessException {@code INVALID_REQUEST} when the encoding is not one of the
     *                             supported ones
     */
    public static Charset resolve(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String requested = name.trim();
        Charset charset = SUPPORTED.get(requested.toUpperCase(Locale.ROOT));
        if (charset == null) {
            throw new FileAccessException(FileAccessErrorCode.INVALID_REQUEST,
                    "The requested encoding is not supported.");
        }
        return charset;
    }

    /**
     * Detects a byte order mark at the beginning of a file.
     *
     * @param head  first bytes of the file
     * @param count number of valid bytes in {@code head}
     * @return the mark or {@code null} when the file starts without one
     */
    public static Bom detectBom(byte[] head, int count) {
        if (count >= 3 && head[0] == UTF_8_BOM[0] && head[1] == UTF_8_BOM[1] && head[2] == UTF_8_BOM[2]) {
            return new Bom(UTF_8, 3);
        }
        if (count >= 2) {
            int first = head[0] & 0xFF;
            int second = head[1] & 0xFF;
            if (first == 0xFF && second == 0xFE) {
                return new Bom(UTF_16LE, 2);
            }
            if (first == 0xFE && second == 0xFF) {
                return new Bom(UTF_16BE, 2);
            }
        }
        return null;
    }

    /**
     * @return {@code true} when the encoding encodes a character in more than one byte and therefore
     *         needs aligned scanning
     */
    public static boolean isUtf16(Charset charset) {
        return charset == StandardCharsets.UTF_16LE || charset == StandardCharsets.UTF_16BE;
    }

    /**
     * @return the number of bytes a single newline occupies in the given encoding
     */
    public static int newlineUnit(Charset charset) {
        return isUtf16(charset) ? 2 : 1;
    }

    /**
     * Whether the bytes at {@code index} are a newline in the given encoding.
     *
     * <p>For UTF-16 the position is checked against the absolute file offset, because a misaligned
     * pair of bytes is a character, not a line break.</p>
     */
    public static boolean isNewlineAt(byte[] buffer, int index, int available, long absoluteOffset, Charset charset) {
        if (charset == StandardCharsets.UTF_16LE) {
            return absoluteOffset % 2 == 0 && index + 1 < available
                    && buffer[index] == 0x0A && buffer[index + 1] == 0x00;
        }
        if (charset == StandardCharsets.UTF_16BE) {
            return absoluteOffset % 2 == 0 && index + 1 < available
                    && buffer[index] == 0x00 && buffer[index + 1] == 0x0A;
        }
        return buffer[index] == 0x0A;
    }

    /**
     * A byte order mark: the encoding it announces and its length in bytes.
     */
    public record Bom(String charsetName, int length) {

        public Charset charset() {
            return SUPPORTED.get(charsetName);
        }
    }
}
