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
}
