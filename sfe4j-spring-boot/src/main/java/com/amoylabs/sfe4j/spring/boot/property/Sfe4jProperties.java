package com.amoylabs.sfe4j.spring.boot.property;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "sfe4j")
public class Sfe4jProperties {
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
