package com.amoylabs.sfe4j.core.access;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Framework independent identity of the caller.
 *
 * <p>It carries the subject identifier and the granted protection rules only. No HTTP session, no
 * plain text password and no host specific type is referenced, so {@code core} stays independent of
 * Servlet and Spring.</p>
 */
public final class FileAccessContext {

    private static final FileAccessContext ANONYMOUS =
            new FileAccessContext("anonymous", Collections.emptyMap());

    private final String subjectId;

    private final Map<String, String> grants;

    private FileAccessContext(String subjectId, Map<String, String> grants) {
        this.subjectId = subjectId;
        this.grants = Collections.unmodifiableMap(new LinkedHashMap<>(grants));
    }

    /**
     * Context without any directory grant. Used by legacy entry points that cannot provide an identity.
     */
    public static FileAccessContext anonymous() {
        return ANONYMOUS;
    }

    /**
     * @param subjectId opaque subject identifier, never {@code null}
     * @param grants    granted rule id to granted rule version mapping
     */
    public static FileAccessContext of(String subjectId, Map<String, String> grants) {
        return new FileAccessContext(subjectId == null ? "anonymous" : subjectId,
                grants == null ? Collections.emptyMap() : grants);
    }

    public String getSubjectId() {
        return subjectId;
    }

    public boolean isAnonymous() {
        return ANONYMOUS.subjectId.equals(subjectId) && grants.isEmpty();
    }

    /**
     * A grant is valid only when the rule version matches: a configuration change invalidates it.
     */
    public boolean hasGrant(DirectoryProtectionRule rule) {
        if (rule == null) {
            return false;
        }
        String grantedVersion = grants.get(rule.getId());
        return grantedVersion != null && grantedVersion.equals(rule.getVersion());
    }
}
