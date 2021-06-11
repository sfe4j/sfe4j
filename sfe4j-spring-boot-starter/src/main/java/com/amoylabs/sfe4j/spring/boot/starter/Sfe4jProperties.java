package com.amoylabs.sfe4j.spring.boot.starter;

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
}
