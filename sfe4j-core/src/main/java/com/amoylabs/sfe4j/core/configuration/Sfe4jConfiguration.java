package com.amoylabs.sfe4j.core.configuration;

import com.amoylabs.sfe4j.core.access.SymbolicLinkPolicy;
import com.amoylabs.sfe4j.core.exception.Sfe4jRuntimeException;

import java.util.Map;

/**
 * Framework independent configuration of SFE4J.
 *
 * <p>Every value has an explicit default and a validation entry point ({@link #validate()}). Directly
 * constructing a core service therefore never bypasses a check because a wrapper type was
 * {@code null}, and never raises a {@link NullPointerException} for a missing value.</p>
 */
public class Sfe4jConfiguration {

    public static final String DEFAULT_BASE_DIR_PATH = "/";

    public static final int DEFAULT_GRANT_TTL_SECONDS = 1800;

    public static final int DEFAULT_PASSWORD_ATTEMPTS_PER_MINUTE = 5;

    public static final long DEFAULT_PREVIEW_MAX_BYTES = 1048576L;

    public static final int DEFAULT_DOWNLOAD_MAX_CONCURRENT = 8;

    public static final boolean DEFAULT_LOGS_ENABLED = false;

    public static final int DEFAULT_LOGS_DEFAULT_LINES = 200;

    public static final int DEFAULT_LOGS_MAX_LINES = 2000;

    public static final long DEFAULT_LOGS_MAX_SCAN_BYTES = 1048576L;

    public static final long DEFAULT_LOGS_POLL_INTERVAL_MS = 1000L;

    public static final int DEFAULT_LOGS_MAX_CONNECTIONS = 20;

    public static final int DEFAULT_LOGS_MAX_CONNECTIONS_PER_SESSION = 2;

    public static final long DEFAULT_LOGS_MAX_CONNECTION_SECONDS = 1800L;

    public static final int DEFAULT_LOGS_MAX_QUEUE_BYTES = 262144;

    public static final int DEFAULT_LOGS_MAX_CHUNK_BYTES = 65536;

    private String title;

    private String description;

    private Map<String, String> quickLinks;

    private String baseDirPath;

    private Boolean restrictToBaseDir;

    private Map<String, String> folderPasswords;

    private SymbolicLinkPolicy symbolicLinkPolicy = SymbolicLinkPolicy.DENY;

    private Integer grantTtlSeconds;

    private Integer passwordAttemptsPerMinute;

    private Long previewMaxBytes;

    private Integer downloadMaxConcurrent;

    private Boolean logsEnabled;

    private Integer logsDefaultLines;

    private Integer logsMaxLines;

    private Long logsMaxScanBytes;

    private Long logsPollIntervalMs;

    private Integer logsMaxConnections;

    private Integer logsMaxConnectionsPerSession;

    private Long logsMaxConnectionSeconds;

    private Integer logsMaxQueueBytes;

    private Integer logsMaxChunkBytes;

    /**
     * Validates the configured ranges.
     *
     * @throws Sfe4jRuntimeException when a value is out of range; the application is expected to fail
     *                               to start instead of silently falling back to a default
     */
    public void validate() {
        checkRange("sfe4j.access.grant-ttl-seconds", getGrantTtlSeconds(), 60, 86400);
        checkRange("sfe4j.access.password-attempts-per-minute", getPasswordAttemptsPerMinute(), 1, 60);
        checkRange("sfe4j.preview.max-bytes", getPreviewMaxBytes(), 1024, 16777216L);
        checkRange("sfe4j.download.max-concurrent", getDownloadMaxConcurrent(), 1, 128);
        checkRange("sfe4j.logs.default-lines", getLogsDefaultLines(), 1, 100000);
        checkRange("sfe4j.logs.max-lines", getLogsMaxLines(), 1, 100000);
        checkRange("sfe4j.logs.max-scan-bytes", getLogsMaxScanBytes(), 4096, 16777216L);
        checkRange("sfe4j.logs.poll-interval-ms", getLogsPollIntervalMs(), 100, 60000);
        checkRange("sfe4j.logs.max-connections", getLogsMaxConnections(), 1, 1000);
        checkRange("sfe4j.logs.max-connections-per-session", getLogsMaxConnectionsPerSession(), 1, 64);
        checkRange("sfe4j.logs.max-connection-seconds", getLogsMaxConnectionSeconds(), 30, 86400);
        checkRange("sfe4j.logs.max-queue-bytes", getLogsMaxQueueBytes(), 4096, 10485760L);
        checkRange("sfe4j.logs.max-chunk-bytes", getLogsMaxChunkBytes(), 1024, 1048576L);
        checkOrder("sfe4j.logs.default-lines", getLogsDefaultLines(), "sfe4j.logs.max-lines", getLogsMaxLines());
        checkOrder("sfe4j.logs.max-connections-per-session", getLogsMaxConnectionsPerSession(),
                "sfe4j.logs.max-connections", getLogsMaxConnections());
        checkOrder("sfe4j.logs.max-chunk-bytes", getLogsMaxChunkBytes(), "sfe4j.logs.max-queue-bytes",
                getLogsMaxQueueBytes());
    }

    private static void checkOrder(String lowKey, long lowValue, String highKey, long highValue) {
        if (lowValue > highValue) {
            throw new Sfe4jRuntimeException(
                    lowKey + " (" + lowValue + ") must not be greater than " + highKey + " (" + highValue + ").");
        }
    }

    private static void checkRange(String key, long value, long min, long max) {
        if (value < min || value > max) {
            throw new Sfe4jRuntimeException(
                    key + " must be between " + min + " and " + max + " but was " + value + ".");
        }
    }

    public Map<String, String> getFolderPasswords() {
        return folderPasswords;
    }

    public void setFolderPasswords(Map<String, String> folderPasswords) {
        this.folderPasswords = folderPasswords;
    }

    /**
     * @return configured base directory, never blank; {@code "/"} when unset
     */
    public String getBaseDirPath() {
        return baseDirPath == null || baseDirPath.trim().isEmpty() ? DEFAULT_BASE_DIR_PATH : baseDirPath;
    }

    public void setBaseDirPath(String baseDirPath) {
        this.baseDirPath = baseDirPath;
    }

    /**
     * @return {@code true} when access is restricted to the base directory; {@code false} when unset
     */
    public boolean getRestrictToBaseDir() {
        return Boolean.TRUE.equals(restrictToBaseDir);
    }

    public void setRestrictToBaseDir(Boolean restrictToBaseDir) {
        this.restrictToBaseDir = restrictToBaseDir;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Map<String, String> getQuickLinks() {
        return quickLinks;
    }

    public void setQuickLinks(Map<String, String> quickLinks) {
        this.quickLinks = quickLinks;
    }

    /**
     * @return symbolic link policy, {@link SymbolicLinkPolicy#DENY} when unset
     */
    public SymbolicLinkPolicy getSymbolicLinkPolicy() {
        return symbolicLinkPolicy == null ? SymbolicLinkPolicy.DENY : symbolicLinkPolicy;
    }

    public void setSymbolicLinkPolicy(SymbolicLinkPolicy symbolicLinkPolicy) {
        this.symbolicLinkPolicy = symbolicLinkPolicy;
    }

    public int getGrantTtlSeconds() {
        return grantTtlSeconds == null ? DEFAULT_GRANT_TTL_SECONDS : grantTtlSeconds;
    }

    public void setGrantTtlSeconds(Integer grantTtlSeconds) {
        this.grantTtlSeconds = grantTtlSeconds;
    }

    public int getPasswordAttemptsPerMinute() {
        return passwordAttemptsPerMinute == null ? DEFAULT_PASSWORD_ATTEMPTS_PER_MINUTE : passwordAttemptsPerMinute;
    }

    public void setPasswordAttemptsPerMinute(Integer passwordAttemptsPerMinute) {
        this.passwordAttemptsPerMinute = passwordAttemptsPerMinute;
    }

    public long getPreviewMaxBytes() {
        return previewMaxBytes == null ? DEFAULT_PREVIEW_MAX_BYTES : previewMaxBytes;
    }

    public void setPreviewMaxBytes(Long previewMaxBytes) {
        this.previewMaxBytes = previewMaxBytes;
    }

    public int getDownloadMaxConcurrent() {
        return downloadMaxConcurrent == null ? DEFAULT_DOWNLOAD_MAX_CONCURRENT : downloadMaxConcurrent;
    }

    public void setDownloadMaxConcurrent(Integer downloadMaxConcurrent) {
        this.downloadMaxConcurrent = downloadMaxConcurrent;
    }

    /**
     * @return {@code true} when text reading and log following are enabled; {@code false} when unset
     */
    public boolean getLogsEnabled() {
        return Boolean.TRUE.equals(logsEnabled);
    }

    public void setLogsEnabled(Boolean logsEnabled) {
        this.logsEnabled = logsEnabled;
    }

    public int getLogsDefaultLines() {
        return logsDefaultLines == null ? DEFAULT_LOGS_DEFAULT_LINES : logsDefaultLines;
    }

    public void setLogsDefaultLines(Integer logsDefaultLines) {
        this.logsDefaultLines = logsDefaultLines;
    }

    public int getLogsMaxLines() {
        return logsMaxLines == null ? DEFAULT_LOGS_MAX_LINES : logsMaxLines;
    }

    public void setLogsMaxLines(Integer logsMaxLines) {
        this.logsMaxLines = logsMaxLines;
    }

    public long getLogsMaxScanBytes() {
        return logsMaxScanBytes == null ? DEFAULT_LOGS_MAX_SCAN_BYTES : logsMaxScanBytes;
    }

    public void setLogsMaxScanBytes(Long logsMaxScanBytes) {
        this.logsMaxScanBytes = logsMaxScanBytes;
    }

    public long getLogsPollIntervalMs() {
        return logsPollIntervalMs == null ? DEFAULT_LOGS_POLL_INTERVAL_MS : logsPollIntervalMs;
    }

    public void setLogsPollIntervalMs(Long logsPollIntervalMs) {
        this.logsPollIntervalMs = logsPollIntervalMs;
    }

    public int getLogsMaxConnections() {
        return logsMaxConnections == null ? DEFAULT_LOGS_MAX_CONNECTIONS : logsMaxConnections;
    }

    public void setLogsMaxConnections(Integer logsMaxConnections) {
        this.logsMaxConnections = logsMaxConnections;
    }

    public int getLogsMaxConnectionsPerSession() {
        return logsMaxConnectionsPerSession == null
                ? DEFAULT_LOGS_MAX_CONNECTIONS_PER_SESSION
                : logsMaxConnectionsPerSession;
    }

    public void setLogsMaxConnectionsPerSession(Integer logsMaxConnectionsPerSession) {
        this.logsMaxConnectionsPerSession = logsMaxConnectionsPerSession;
    }

    public long getLogsMaxConnectionSeconds() {
        return logsMaxConnectionSeconds == null ? DEFAULT_LOGS_MAX_CONNECTION_SECONDS : logsMaxConnectionSeconds;
    }

    public void setLogsMaxConnectionSeconds(Long logsMaxConnectionSeconds) {
        this.logsMaxConnectionSeconds = logsMaxConnectionSeconds;
    }

    public int getLogsMaxQueueBytes() {
        return logsMaxQueueBytes == null ? DEFAULT_LOGS_MAX_QUEUE_BYTES : logsMaxQueueBytes;
    }

    public void setLogsMaxQueueBytes(Integer logsMaxQueueBytes) {
        this.logsMaxQueueBytes = logsMaxQueueBytes;
    }

    public int getLogsMaxChunkBytes() {
        return logsMaxChunkBytes == null ? DEFAULT_LOGS_MAX_CHUNK_BYTES : logsMaxChunkBytes;
    }

    public void setLogsMaxChunkBytes(Integer logsMaxChunkBytes) {
        this.logsMaxChunkBytes = logsMaxChunkBytes;
    }
}
