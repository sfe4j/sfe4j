package com.amoylabs.sfe4j.spring.boot.logs;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Integrity checked resume token of a follow connection.
 *
 * <p>The token is bound to the subject, the requested path, the encoding, the file generation and a
 * byte position, and it expires. It never carries a permission: every follow request is authorized
 * again, so a forged or stale token only produces a gap event and a fresh snapshot.</p>
 */
public class FollowCursorCodec {

    private static final String ALGORITHM = "HmacSHA256";

    private static final long TTL_MILLIS = 300_000L;

    private static final String VERSION = "v1";

    private final byte[] key;

    private final java.util.function.LongSupplier clock;

    public FollowCursorCodec() {
        this(randomKey(), System::currentTimeMillis);
    }

    FollowCursorCodec(byte[] key, java.util.function.LongSupplier clock) {
        this.key = key.clone();
        this.clock = clock;
    }

    /**
     * @return a token for the given position, or {@code null} when it cannot be signed
     */
    public String encode(String subjectId, String path, String charset, String generation, long position) {
        long expiresAt = clock.getAsLong() + TTL_MILLIS;
        String payload = String.join("|", VERSION, orEmpty(subjectId), orEmpty(path), orEmpty(charset),
                orEmpty(generation), String.valueOf(position), String.valueOf(expiresAt));
        String body = base64(payload.getBytes(StandardCharsets.UTF_8));
        return body + "." + sign(body);
    }

    /**
     * Verifies a token and returns its content.
     *
     * @return the cursor, or {@code null} when the token is malformed, forged, expired or bound to
     *         another subject, path, encoding or file generation
     */
    public Cursor decode(String token, String subjectId, String path, String charset, String generation) {
        if (token == null || token.isBlank()) {
            return null;
        }
        int separator = token.indexOf('.');
        if (separator <= 0 || separator == token.length() - 1) {
            return null;
        }
        String body = token.substring(0, separator);
        String signature = token.substring(separator + 1);
        if (!constantTimeEquals(sign(body), signature)) {
            return null;
        }

        String payload;
        try {
            payload = new String(Base64.getUrlDecoder().decode(body), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }

        String[] fields = payload.split("\\|", -1);
        if (fields.length != 7 || !VERSION.equals(fields[0])) {
            return null;
        }
        if (!orEmpty(subjectId).equals(fields[1]) || !orEmpty(path).equals(fields[2])
                || !orEmpty(charset).equals(fields[3]) || !orEmpty(generation).equals(fields[4])) {
            return null;
        }

        long position;
        long expiresAt;
        try {
            position = Long.parseLong(fields[5]);
            expiresAt = Long.parseLong(fields[6]);
        } catch (NumberFormatException e) {
            return null;
        }
        if (expiresAt <= clock.getAsLong() || position < 0L) {
            return null;
        }
        return new Cursor(position);
    }

    private String sign(String body) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key, ALGORITHM));
            return base64(mac.doFinal(body.getBytes(StandardCharsets.US_ASCII)));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("The follow cursor cannot be signed.", e);
        }
    }

    private static byte[] randomKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return key;
    }

    private static String base64(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                actual.getBytes(StandardCharsets.US_ASCII));
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * Verified content of a resume token.
     */
    public record Cursor(long position) {
    }
}
