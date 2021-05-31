package com.amoylabs.sfe4j.spring.boot.configuration;

public class Sfe4jConfiguration {
    private String baseDirPath;
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
