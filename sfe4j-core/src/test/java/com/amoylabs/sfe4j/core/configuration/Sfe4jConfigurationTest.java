package com.amoylabs.sfe4j.core.configuration;

import com.amoylabs.sfe4j.core.access.SymbolicLinkPolicy;
import com.amoylabs.sfe4j.core.exception.Sfe4jRuntimeException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Sfe4jConfigurationTest {

    @Test
    void defaultsAreExplicitAndNonNull() {
        Sfe4jConfiguration configuration = new Sfe4jConfiguration();

        assertEquals("/", configuration.getBaseDirPath());
        assertFalse(configuration.getRestrictToBaseDir());
        assertEquals(SymbolicLinkPolicy.DENY, configuration.getSymbolicLinkPolicy());
        assertEquals(1800, configuration.getGrantTtlSeconds());
        assertEquals(5, configuration.getPasswordAttemptsPerMinute());
        assertEquals(1048576L, configuration.getPreviewMaxBytes());
        assertEquals(8, configuration.getDownloadMaxConcurrent());
        assertFalse(configuration.getLogsEnabled());
        assertEquals(200, configuration.getLogsDefaultLines());
        assertEquals(2000, configuration.getLogsMaxLines());
        assertEquals(1048576L, configuration.getLogsMaxScanBytes());
        assertEquals(1000L, configuration.getLogsPollIntervalMs());
        assertEquals(20, configuration.getLogsMaxConnections());
        assertEquals(2, configuration.getLogsMaxConnectionsPerSession());
        assertEquals(1800L, configuration.getLogsMaxConnectionSeconds());
        assertEquals(262144, configuration.getLogsMaxQueueBytes());
        assertEquals(65536, configuration.getLogsMaxChunkBytes());
        configuration.validate();
    }

    @Test
    void nullWrapperValuesDoNotBypassChecks() {
        Sfe4jConfiguration configuration = new Sfe4jConfiguration();
        configuration.setRestrictToBaseDir(null);
        configuration.setSymbolicLinkPolicy(null);
        configuration.setGrantTtlSeconds(null);
        configuration.setPreviewMaxBytes(null);

        assertFalse(configuration.getRestrictToBaseDir());
        assertEquals(SymbolicLinkPolicy.DENY, configuration.getSymbolicLinkPolicy());
        assertEquals(1800, configuration.getGrantTtlSeconds());
        assertEquals(1048576L, configuration.getPreviewMaxBytes());
        configuration.validate();
    }

    @Test
    void blankBaseDirectoryFallsBackToRoot() {
        Sfe4jConfiguration configuration = new Sfe4jConfiguration();
        configuration.setBaseDirPath("   ");

        assertEquals("/", configuration.getBaseDirPath());
        configuration.validate();
    }

    @Test
    void grantTtlOutOfRangeIsRejected() {
        Sfe4jConfiguration configuration = new Sfe4jConfiguration();
        configuration.setGrantTtlSeconds(10);

        Sfe4jRuntimeException exception = assertThrows(Sfe4jRuntimeException.class, configuration::validate);
        assertTrue(exception.getMessage().contains("sfe4j.access.grant-ttl-seconds"), exception.getMessage());
    }

    @Test
    void passwordAttemptsOutOfRangeIsRejected() {
        Sfe4jConfiguration configuration = new Sfe4jConfiguration();
        configuration.setPasswordAttemptsPerMinute(0);

        assertThrows(Sfe4jRuntimeException.class, configuration::validate);
    }

    @Test
    void previewMaxBytesOutOfRangeIsRejected() {
        Sfe4jConfiguration tooSmall = new Sfe4jConfiguration();
        tooSmall.setPreviewMaxBytes(10L);
        assertThrows(Sfe4jRuntimeException.class, tooSmall::validate);

        Sfe4jConfiguration tooLarge = new Sfe4jConfiguration();
        tooLarge.setPreviewMaxBytes(100_000_000L);
        assertThrows(Sfe4jRuntimeException.class, tooLarge::validate);
    }

    @Test
    void downloadConcurrencyOutOfRangeIsRejected() {
        Sfe4jConfiguration tooMany = new Sfe4jConfiguration();
        tooMany.setDownloadMaxConcurrent(500);
        assertThrows(Sfe4jRuntimeException.class, tooMany::validate);

        Sfe4jConfiguration none = new Sfe4jConfiguration();
        none.setDownloadMaxConcurrent(0);
        assertThrows(Sfe4jRuntimeException.class, none::validate);
    }

    @Test
    void logValuesOutOfRangeAreRejected() {
        Sfe4jConfiguration lines = new Sfe4jConfiguration();
        lines.setLogsMaxLines(0);
        assertThrows(Sfe4jRuntimeException.class, lines::validate);

        Sfe4jConfiguration scan = new Sfe4jConfiguration();
        scan.setLogsMaxScanBytes(1024L);
        assertThrows(Sfe4jRuntimeException.class, scan::validate);

        Sfe4jConfiguration poll = new Sfe4jConfiguration();
        poll.setLogsPollIntervalMs(10L);
        assertThrows(Sfe4jRuntimeException.class, poll::validate);

        Sfe4jConfiguration lifetime = new Sfe4jConfiguration();
        lifetime.setLogsMaxConnectionSeconds(5L);
        assertThrows(Sfe4jRuntimeException.class, lifetime::validate);

        Sfe4jConfiguration queue = new Sfe4jConfiguration();
        queue.setLogsMaxQueueBytes(1024);
        assertThrows(Sfe4jRuntimeException.class, queue::validate);
    }

    @Test
    void perSessionLimitMayNotExceedTheInstanceLimit() {
        Sfe4jConfiguration configuration = new Sfe4jConfiguration();
        configuration.setLogsMaxConnections(2);
        configuration.setLogsMaxConnectionsPerSession(3);

        Sfe4jRuntimeException exception = assertThrows(Sfe4jRuntimeException.class, configuration::validate);
        assertTrue(exception.getMessage().contains("sfe4j.logs.max-connections-per-session"),
                exception.getMessage());
    }

    @Test
    void chunkMayNotExceedTheQueue() {
        Sfe4jConfiguration configuration = new Sfe4jConfiguration();
        configuration.setLogsMaxQueueBytes(65536);
        configuration.setLogsMaxChunkBytes(131072);

        Sfe4jRuntimeException exception = assertThrows(Sfe4jRuntimeException.class, configuration::validate);
        assertTrue(exception.getMessage().contains("sfe4j.logs.max-chunk-bytes"), exception.getMessage());
    }

    @Test
    void defaultLinesMayNotExceedTheMaximum() {
        Sfe4jConfiguration configuration = new Sfe4jConfiguration();
        configuration.setLogsMaxLines(100);
        configuration.setLogsDefaultLines(500);

        Sfe4jRuntimeException exception = assertThrows(Sfe4jRuntimeException.class, configuration::validate);
        assertTrue(exception.getMessage().contains("sfe4j.logs.default-lines"), exception.getMessage());
    }
}
