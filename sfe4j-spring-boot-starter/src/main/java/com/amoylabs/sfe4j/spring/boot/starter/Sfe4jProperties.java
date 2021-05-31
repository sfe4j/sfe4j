package com.amoylabs.sfe4j.spring.boot.starter;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sfe4j")
public class Sfe4jProperties {
    /**
     * Full path of base directory, e.g. "c:/dev" for Windows, "/dev" for Linux or MacOS
     */
    private String baseDirPath;

    /**
     * Whether restrict the access to base directory only or not.
     */
    private Boolean restrictToBaseDir;

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
