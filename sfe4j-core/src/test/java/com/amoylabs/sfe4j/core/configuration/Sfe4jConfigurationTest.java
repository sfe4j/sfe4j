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
}
