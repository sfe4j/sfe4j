package com.amoylabs.sfe4j.spring.boot.access;

import com.amoylabs.sfe4j.core.access.DirectoryProtectionRule;
import com.amoylabs.sfe4j.core.access.FileAccessContext;
import com.amoylabs.sfe4j.core.access.FileAccessService;
import com.amoylabs.sfe4j.core.exception.FileAccessErrorCode;
import com.amoylabs.sfe4j.core.exception.FileAccessException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Adapts the HTTP session to the framework independent access context and implements the directory
 * password challenge.
 */
public class DirectoryAuthorizationService {

    private final FileAccessService fileAccessService;

    private final SessionGrantStore grantStore;

    private final PasswordAttemptLimiter attemptLimiter;

    public DirectoryAuthorizationService(FileAccessService fileAccessService, SessionGrantStore grantStore,
                                        PasswordAttemptLimiter attemptLimiter) {
        this.fileAccessService = fileAccessService;
        this.grantStore = grantStore;
        this.attemptLimiter = attemptLimiter;
    }

    public FileAccessContext contextFor(HttpSession session) {
        return grantStore.contextFor(session);
    }

    /**
     * Rules that protect the requested path, matching both the lexical and the real path.
     */
    public List<DirectoryProtectionRule> requiredRules(String requestedPath) {
        return fileAccessService.rulesProtecting(requestedPath);
    }

    /**
     * @return {@code true} when at least one protecting rule has no valid grant
     */
    public boolean isChallengeRequired(String requestedPath, HttpSession session) {
        List<DirectoryProtectionRule> rules = requiredRules(requestedPath);
        if (rules.isEmpty()) {
            return false;
        }
        FileAccessContext context = grantStore.contextFor(session);
        for (DirectoryProtectionRule rule : rules) {
            if (!context.hasGrant(rule)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Verifies the submitted password and grants the matching rules.
     *
     * <p>Attempts are limited per session and client address. On success the session identifier is
     * changed to prevent session fixation, the grants are stored, and the attempt window is reset.</p>
     *
     * @return {@code false} when the password does not match
     * @throws FileAccessException {@code RATE_LIMITED} when the attempt limit is exhausted
     */
    public boolean authenticate(HttpServletRequest request, String requestedPath, String password) {
        HttpSession session = request.getSession(true);
        String attemptKey = attemptKey(request, session);

        if (!attemptLimiter.tryAcquire(attemptKey)) {
            throw new FileAccessException(FileAccessErrorCode.RATE_LIMITED,
                    "Too many password attempts. Please try again in a minute.");
        }

        List<DirectoryProtectionRule> rules = requiredRules(requestedPath);
        if (rules.isEmpty()) {
            return true;
        }
        if (!matches(rules, password)) {
            return false;
        }

        request.changeSessionId();
        grantStore.grant(session, rules);
        attemptLimiter.reset(attemptKey(request, session));
        return true;
    }

    /**
     * Removes the SFE4J grants without invalidating the host session.
     */
    public void revoke(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            grantStore.revoke(session);
        }
    }

    private boolean matches(List<DirectoryProtectionRule> rules, String password) {
        if (password == null) {
            return false;
        }
        boolean matched = true;
        for (DirectoryProtectionRule rule : rules) {
            matched &= constantTimeEquals(rule.getPassword(), password);
        }
        return matched;
    }

    private static boolean constantTimeEquals(String expected, String submitted) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                submitted.getBytes(StandardCharsets.UTF_8));
    }

    private static String attemptKey(HttpServletRequest request, HttpSession session) {
        // Only the transport peer address is trusted; X-Forwarded-For is client controlled.
        return session.getId() + "|" + request.getRemoteAddr();
    }
}
