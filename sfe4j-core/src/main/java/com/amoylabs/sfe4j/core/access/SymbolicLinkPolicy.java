package com.amoylabs.sfe4j.core.access;

import com.amoylabs.sfe4j.core.exception.Sfe4jRuntimeException;

/**
 * Policy applied to symbolic links found between the effective root and the requested target.
 */
public enum SymbolicLinkPolicy {

    /**
     * Any symbolic link between the effective root and the target is rejected.
     */
    DENY("deny"),

    /**
     * Symbolic links are followed, but the resolved target must still stay inside the base
     * directory when {@code restrict-to-base-dir} is enabled.
     */
    WITHIN_ROOT("within-root");

    private final String value;

    SymbolicLinkPolicy(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * Parses the configured value.
     *
     * @param raw configured value, {@code null} or blank means {@link #DENY}
     * @return the matching policy
     * @throws Sfe4jRuntimeException if the value is not supported
     */
    public static SymbolicLinkPolicy from(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return DENY;
        }
        String normalized = raw.trim().toLowerCase(java.util.Locale.ROOT);
        for (SymbolicLinkPolicy policy : values()) {
            if (policy.value.equals(normalized)) {
                return policy;
            }
        }
        throw new Sfe4jRuntimeException(
                "Unsupported value for sfe4j.access.symbolic-link-policy: '" + raw + "', expected 'deny' or 'within-root'.");
    }
}
