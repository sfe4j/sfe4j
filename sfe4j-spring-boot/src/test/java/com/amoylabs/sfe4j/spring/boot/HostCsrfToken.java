package com.amoylabs.sfe4j.spring.boot;

/**
 * Stand-in for the Spring Security {@code CsrfToken} attribute, used to verify host compatibility
 * without adding a dependency on Spring Security.
 */
public class HostCsrfToken {

    private final String token;

    public HostCsrfToken(String token) {
        this.token = token;
    }

    public String getToken() {
        return token;
    }
}
