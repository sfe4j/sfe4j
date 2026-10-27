package com.amoylabs.sfe4j.spring.boot.starter;

import com.amoylabs.sfe4j.core.access.SymbolicLinkPolicy;
import com.amoylabs.sfe4j.core.configuration.Sfe4jConfiguration;

import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "sfe4j")
public class Sfe4jProperties {

    /**
     * Title of the file-explorer page
     */
    private String title;

    /**
     * Description of the file-explorer page
     */
    private String description;

    /**
     * Quick links showed on top-left of the file-explorer page
     */
    private Map<String, String> quickLinks;

    /**
     * Full path of base directory, e.g. "c:/" for Windows, "/" for Linux or MacOS
     */
    private String baseDirPath;

    /**
     * Whether restrict the access to base directory only or not.
     */
    private Boolean restrictToBaseDir;

    /**
     * Passwords for folder protection. Key is folder path, value is password.
     */
    private Map<String, String> folderPasswords;

    /**
     * Access control settings.
     */
    private Access access = new Access();

    /**
     * Preview settings.
     */
    private Preview preview = new Preview();

    /**
     * Download settings.
     */
    private Download download = new Download();

    /**
     * Text read and log following settings.
     */
    private Logs logs = new Logs();

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

    public String getBaseDirPath() {
        return baseDirPath;
    }

    public void setBaseDirPath(String baseDirPath) {
        this.baseDirPath = baseDirPath;
    }

    public Boolean getRestrictToBaseDir() {
        return restrictToBaseDir;
    }

    public void setRestrictToBaseDir(Boolean restrictToBaseDir) {
        this.restrictToBaseDir = restrictToBaseDir;
    }

    public Map<String, String> getFolderPasswords() {
        return folderPasswords;
    }

    public void setFolderPasswords(Map<String, String> folderPasswords) {
        this.folderPasswords = folderPasswords;
    }

    /**
     * Converts the bound properties into the framework independent configuration.
     *
     * <p>Every missing value falls back to a documented default, and every value is validated: a
     * configuration mistake stops the application instead of silently weakening a security setting.</p>
     */
    public Sfe4jConfiguration toConfiguration() {
        Sfe4jConfiguration configuration = new Sfe4jConfiguration();
        configuration.setTitle(title);
        configuration.setDescription(description);
        configuration.setQuickLinks(quickLinks);
        configuration.setBaseDirPath(
                StringUtils.isNotEmpty(baseDirPath) ? baseDirPath : Sfe4jConfiguration.DEFAULT_BASE_DIR_PATH);
        configuration.setRestrictToBaseDir(Boolean.TRUE.equals(restrictToBaseDir));
        configuration.setFolderPasswords(folderPasswords);
        configuration.setSymbolicLinkPolicy(SymbolicLinkPolicy.from(access.getSymbolicLinkPolicy()));
        configuration.setGrantTtlSeconds(access.getGrantTtlSeconds());
        configuration.setPasswordAttemptsPerMinute(access.getPasswordAttemptsPerMinute());
        configuration.setPreviewMaxBytes(preview.getMaxBytes());
        configuration.setDownloadMaxConcurrent(download.getMaxConcurrent());
        configuration.setLogsEnabled(logs.getEnabled());
        configuration.setLogsDefaultLines(logs.getDefaultLines());
        configuration.setLogsMaxLines(logs.getMaxLines());
        configuration.setLogsMaxScanBytes(logs.getMaxScanBytes());
        configuration.setLogsPollIntervalMs(logs.getPollIntervalMs());
        configuration.setLogsMaxConnections(logs.getMaxConnections());
        configuration.setLogsMaxConnectionsPerSession(logs.getMaxConnectionsPerSession());
        configuration.setLogsMaxConnectionSeconds(logs.getMaxConnectionSeconds());
        configuration.setLogsMaxQueueBytes(logs.getMaxQueueBytes());
        configuration.setLogsMaxChunkBytes(logs.getMaxChunkBytes());

        configuration.validate();
        return configuration;
    }

    public Access getAccess() {
        return access;
    }

    public void setAccess(Access access) {
        this.access = access == null ? new Access() : access;
    }

    public Preview getPreview() {
        return preview;
    }

    public void setPreview(Preview preview) {
        this.preview = preview == null ? new Preview() : preview;
    }

    public Download getDownload() {
        return download;
    }

    public void setDownload(Download download) {
        this.download = download == null ? new Download() : download;
    }

    public Logs getLogs() {
        return logs;
    }

    public void setLogs(Logs logs) {
        this.logs = logs == null ? new Logs() : logs;
    }

    public static class Access {

        /**
         * Symbolic link policy between the base directory and the target: "deny" or "within-root".
         * Default: deny.
         */
        private String symbolicLinkPolicy;

        /**
         * Validity of a directory grant in seconds, between 60 and 86400. Default: 1800.
         */
        private Integer grantTtlSeconds;

        /**
         * Password attempts per minute and per session/client address, between 1 and 60. Default: 5.
         */
        private Integer passwordAttemptsPerMinute;

        public String getSymbolicLinkPolicy() {
            return symbolicLinkPolicy;
        }

        public void setSymbolicLinkPolicy(String symbolicLinkPolicy) {
            this.symbolicLinkPolicy = symbolicLinkPolicy;
        }

        public Integer getGrantTtlSeconds() {
            return grantTtlSeconds;
        }

        public void setGrantTtlSeconds(Integer grantTtlSeconds) {
            this.grantTtlSeconds = grantTtlSeconds;
        }

        public Integer getPasswordAttemptsPerMinute() {
            return passwordAttemptsPerMinute;
        }

        public void setPasswordAttemptsPerMinute(Integer passwordAttemptsPerMinute) {
            this.passwordAttemptsPerMinute = passwordAttemptsPerMinute;
        }
    }

    public static class Preview {

        /**
         * Maximum number of bytes read for a text preview, between 1024 and 16777216. Default: 1048576.
         */
        private Long maxBytes;

        public Long getMaxBytes() {
            return maxBytes;
        }

        public void setMaxBytes(Long maxBytes) {
            this.maxBytes = maxBytes;
        }
    }

    public static class Download {

        /**
         * Maximum number of concurrent downloads, between 1 and 128. Default: 8.
         */
        private Integer maxConcurrent;

        public Integer getMaxConcurrent() {
            return maxConcurrent;
        }

        public void setMaxConcurrent(Integer maxConcurrent) {
            this.maxConcurrent = maxConcurrent;
        }
    }

    public static class Logs {

        /**
         * Whether the text read API and log following are enabled. Default: false.
         */
        private Boolean enabled;

        /**
         * Number of lines read when a request does not ask for a specific amount, between 1 and
         * max-lines. Default: 200.
         */
        private Integer defaultLines;

        /**
         * Upper bound of the lines one read may return, between 1 and 100000. Default: 2000.
         */
        private Integer maxLines;

        /**
         * Upper bound of the bytes scanned backwards for a tail read, between 4096 and 16777216.
         * Default: 1048576.
         */
        private Long maxScanBytes;

        /**
         * Interval between two read rounds of a follow connection in milliseconds, between 100 and
         * 60000. Default: 1000.
         */
        private Long pollIntervalMs;

        /**
         * Maximum number of follow connections per instance, between 1 and 1000. Default: 20.
         */
        private Integer maxConnections;

        /**
         * Maximum number of follow connections per session, between 1 and 64 and not greater than
         * max-connections. Default: 2.
         */
        private Integer maxConnectionsPerSession;

        /**
         * Lifetime of one follow connection in seconds, between 30 and 86400. Default: 1800.
         */
        private Long maxConnectionSeconds;

        /**
         * Upper bound of the bytes queued for one follow connection, between 4096 and 10485760.
         * Default: 262144.
         */
        private Integer maxQueueBytes;

        /**
         * Upper bound of the bytes read in one round of a follow connection, between 1024 and 1048576
         * and not greater than max-queue-bytes. Default: 65536.
         */
        private Integer maxChunkBytes;

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        public Integer getDefaultLines() {
            return defaultLines;
        }

        public void setDefaultLines(Integer defaultLines) {
            this.defaultLines = defaultLines;
        }

        public Integer getMaxLines() {
            return maxLines;
        }

        public void setMaxLines(Integer maxLines) {
            this.maxLines = maxLines;
        }

        public Long getMaxScanBytes() {
            return maxScanBytes;
        }

        public void setMaxScanBytes(Long maxScanBytes) {
            this.maxScanBytes = maxScanBytes;
        }

        public Long getPollIntervalMs() {
            return pollIntervalMs;
        }

        public void setPollIntervalMs(Long pollIntervalMs) {
            this.pollIntervalMs = pollIntervalMs;
        }

        public Integer getMaxConnections() {
            return maxConnections;
        }

        public void setMaxConnections(Integer maxConnections) {
            this.maxConnections = maxConnections;
        }

        public Integer getMaxConnectionsPerSession() {
            return maxConnectionsPerSession;
        }

        public void setMaxConnectionsPerSession(Integer maxConnectionsPerSession) {
            this.maxConnectionsPerSession = maxConnectionsPerSession;
        }

        public Long getMaxConnectionSeconds() {
            return maxConnectionSeconds;
        }

        public void setMaxConnectionSeconds(Long maxConnectionSeconds) {
            this.maxConnectionSeconds = maxConnectionSeconds;
        }

        public Integer getMaxQueueBytes() {
            return maxQueueBytes;
        }

        public void setMaxQueueBytes(Integer maxQueueBytes) {
            this.maxQueueBytes = maxQueueBytes;
        }

        public Integer getMaxChunkBytes() {
            return maxChunkBytes;
        }

        public void setMaxChunkBytes(Integer maxChunkBytes) {
            this.maxChunkBytes = maxChunkBytes;
        }
    }
}
