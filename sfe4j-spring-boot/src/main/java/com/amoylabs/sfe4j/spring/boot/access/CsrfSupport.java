package com.amoylabs.sfe4j.spring.boot.access;

import com.amoylabs.sfe4j.core.exception.FileAccessErrorCode;
import com.amoylabs.sfe4j.core.exception.FileAccessException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * CSRF token support for the password form and the logout action.
 *
 * <p>When the host application already publishes a Spring Security {@code CsrfToken} as request
 * attribute {@code _csrf}, its token is rendered and the host filter keeps validating it. Otherwise an
 * independent session token is used.</p>
 *
 * <p>A submission without a prior session is always rejected: a missing session must never be treated
 * as a valid state.</p>
 */
public class CsrfSupport {

    public static final String PARAMETER_NAME = "_csrf";

    private static final String SESSION_ATTRIBUTE = "com.amoylabs.sfe4j.web.CSRF_TOKEN";

    private static final String HOST_ATTRIBUTE = "_csrf";

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * @return token to render in the form
     */
    public String token(HttpServletRequest request) {
        String hostToken = hostToken(request);
        if (hostToken != null) {
            return hostToken;
        }
        HttpSession session = request.getSession(true);
        synchronized (session) {
            Object existing = session.getAttribute(SESSION_ATTRIBUTE);
            if (existing instanceof String && !((String) existing).isEmpty()) {
                return (String) existing;
            }
            String generated = generateToken();
            session.setAttribute(SESSION_ATTRIBUTE, generated);
            return generated;
        }
    }

    /**
     * @return {@code true} when the token is managed and validated by the host framework
     */
    public boolean isHostManaged(HttpServletRequest request) {
        return hostToken(request) != null;
    }

    /**
     * Verifies the submitted token.
     *
     * @throws FileAccessException {@code INVALID_REQUEST} when the token is missing or wrong
     */
    public void verify(HttpServletRequest request) {
        if (isHostManaged(request)) {
            return;
        }
        HttpSession session = request.getSession(false);
        String expected = session == null ? null : (String) session.getAttribute(SESSION_ATTRIBUTE);
        if (expected == null) {
            throw new FileAccessException(FileAccessErrorCode.INVALID_REQUEST,
                    "The request cannot be verified. Reload the page and try again.");
        }
        String submitted = request.getParameter(PARAMETER_NAME);
        if (submitted == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                submitted.getBytes(StandardCharsets.UTF_8))) {
            throw new FileAccessException(FileAccessErrorCode.INVALID_REQUEST,
                    "The request cannot be verified. Reload the page and try again.");
        }
    }

    /**
     * Replaces the session token, called after a successful authentication.
     */
    public void rotate(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.setAttribute(SESSION_ATTRIBUTE, generateToken());
        }
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Reads the host {@code CsrfToken} through reflection so the module keeps working without Spring
     * Security on the classpath.
     */
    private String hostToken(HttpServletRequest request) {
        Object attribute;
        try {
            attribute = request.getAttribute(HOST_ATTRIBUTE);
        } catch (RuntimeException e) {
            return null;
        }
        if (attribute == null) {
            return null;
        }
        try {
            Method getToken = attribute.getClass().getMethod("getToken");
            Object value = getToken.invoke(attribute);
            if (value instanceof String && !((String) value).isEmpty()) {
                return (String) value;
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
        return null;
    }
}
