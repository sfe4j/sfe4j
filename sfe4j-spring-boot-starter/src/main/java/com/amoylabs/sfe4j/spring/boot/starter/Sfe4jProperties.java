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
}
