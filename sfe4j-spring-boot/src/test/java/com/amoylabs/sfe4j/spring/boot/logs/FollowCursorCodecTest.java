package com.amoylabs.sfe4j.spring.boot.logs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class FollowCursorCodecTest {

    private static final String SUBJECT = "session-1";

    private static final String PATH = "/data/logs/app.log";

    private static final String CHARSET = "UTF-8";

    private static final String GENERATION = "k:12345";

    private final FollowCursorCodec codec = new FollowCursorCodec();

    @Test
    void roundTripKeepsThePosition() {
        String token = codec.encode(SUBJECT, PATH, CHARSET, GENERATION, 4096L);

        FollowCursorCodec.Cursor cursor = codec.decode(token, SUBJECT, PATH, CHARSET, GENERATION);

        assertNotNull(cursor);
        assertEquals(4096L, cursor.position());
    }

    @Test
    void forgedSignatureIsRejected() {
        String token = codec.encode(SUBJECT, PATH, CHARSET, GENERATION, 10L);
        String forged = token.substring(0, token.length() - 2) + "xy";

        assertNull(codec.decode(forged, SUBJECT, PATH, CHARSET, GENERATION));
    }

    @Test
    void tokenBoundToAnotherSubjectIsRejected() {
        String token = codec.encode(SUBJECT, PATH, CHARSET, GENERATION, 10L);

        assertNull(codec.decode(token, "session-2", PATH, CHARSET, GENERATION));
    }

    @Test
    void tokenBoundToAnotherPathIsRejected() {
        String token = codec.encode(SUBJECT, PATH, CHARSET, GENERATION, 10L);

        assertNull(codec.decode(token, SUBJECT, "/data/logs/other.log", CHARSET, GENERATION));
    }

    @Test
    void tokenBoundToAnotherEncodingIsRejected() {
        String token = codec.encode(SUBJECT, PATH, CHARSET, GENERATION, 10L);

        assertNull(codec.decode(token, SUBJECT, PATH, "UTF-16LE", GENERATION));
    }

    @Test
    void tokenBoundToAnotherGenerationIsRejected() {
        String token = codec.encode(SUBJECT, PATH, CHARSET, GENERATION, 10L);

        assertNull(codec.decode(token, SUBJECT, PATH, CHARSET, "k:99999"));
    }

    @Test
    void malformedTokenIsRejected() {
        assertNull(codec.decode("not-a-token", SUBJECT, PATH, CHARSET, GENERATION));
        assertNull(codec.decode("", SUBJECT, PATH, CHARSET, GENERATION));
        assertNull(codec.decode(null, SUBJECT, PATH, CHARSET, GENERATION));
    }

    @Test
    void expiredTokenIsRejected() {
        long[] now = {1_000_000L};
        FollowCursorCodec expiring = new FollowCursorCodec(new byte[]{1, 2, 3}, () -> now[0]);
        String token = expiring.encode(SUBJECT, PATH, CHARSET, GENERATION, 10L);

        now[0] += 600_000L;

        assertNull(expiring.decode(token, SUBJECT, PATH, CHARSET, GENERATION));
    }
}
