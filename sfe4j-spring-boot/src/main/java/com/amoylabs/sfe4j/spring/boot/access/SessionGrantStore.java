package com.amoylabs.sfe4j.spring.boot.access;

import com.amoylabs.sfe4j.core.access.DirectoryProtectionRegistry;
import com.amoylabs.sfe4j.core.access.DirectoryProtectionRule;
import com.amoylabs.sfe4j.core.access.FileAccessContext;
import com.amoylabs.sfe4j.core.configuration.Sfe4jConfiguration;

import jakarta.servlet.http.HttpSession;

import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stores directory grants in the host HTTP session.
 *
 * <p>Only the rule id, the rule version and the expiry instant are stored; the password is never
 * persisted. A grant expires after {@code sfe4j.access.grant-ttl-seconds} and is invalidated as soon as
 * the rule version changes, which happens when the protection configuration is modified.</p>
 */
public class SessionGrantStore {

    private static final String ATTRIBUTE = "com.amoylabs.sfe4j.access.GRANTS";

    private final Sfe4jConfiguration configuration;

    private final DirectoryProtectionRegistry registry;

    private final java.util.function.LongSupplier clock;

    public SessionGrantStore(Sfe4jConfiguration configuration, DirectoryProtectionRegistry registry) {
        this(configuration, registry, System::currentTimeMillis);
    }

    SessionGrantStore(Sfe4jConfiguration configuration, DirectoryProtectionRegistry registry,
                      java.util.function.LongSupplier clock) {
        this.configuration = configuration;
        this.registry = registry;
        this.clock = clock;
    }

    /**
     * Builds the access context of the current session, dropping expired and outdated grants.
     */
    public FileAccessContext contextFor(HttpSession session) {
        if (session == null) {
            return FileAccessContext.anonymous();
        }

        Map<String, Grant> grants = read(session);
        if (grants.isEmpty()) {
            return FileAccessContext.of(session.getId(), Map.of());
        }

        long now = clock.getAsLong();
        Map<String, String> valid = new LinkedHashMap<>();
        boolean changed = false;

        Iterator<Map.Entry<String, Grant>> iterator = grants.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Grant> entry = iterator.next();
            Grant grant = entry.getValue();
            DirectoryProtectionRule rule = registry.findById(entry.getKey());
            if (grant == null || rule == null || !rule.getVersion().equals(grant.version()) || grant.expiresAt() <= now) {
                iterator.remove();
                changed = true;
                continue;
            }
            valid.put(entry.getKey(), grant.version());
        }

        if (changed) {
            write(session, grants);
        }
        return FileAccessContext.of(session.getId(), valid);
    }

    /**
     * Grants every given rule for the configured time to live.
     */
    public void grant(HttpSession session, Collection<DirectoryProtectionRule> rules) {
        if (session == null || rules == null || rules.isEmpty()) {
            return;
        }
        long expiresAt = clock.getAsLong() + configuration.getGrantTtlSeconds() * 1000L;
        Map<String, Grant> grants = read(session);
        for (DirectoryProtectionRule rule : rules) {
            grants.put(rule.getId(), new Grant(rule.getId(), rule.getVersion(), expiresAt));
        }
        write(session, grants);
    }

    /**
     * Removes only the SFE4J grants; the host session stays alive.
     */
    public void revoke(HttpSession session) {
        if (session != null) {
            session.removeAttribute(ATTRIBUTE);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Grant> read(HttpSession session) {
        Object attribute = session.getAttribute(ATTRIBUTE);
        if (!(attribute instanceof Map)) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>((Map<String, Grant>) attribute);
    }

    private void write(HttpSession session, Map<String, Grant> grants) {
        session.setAttribute(ATTRIBUTE, new LinkedHashMap<>(grants));
    }

    /**
     * Grant persisted in the session; {@link Serializable} so a serializable session keeps working.
     */
    private record Grant(String ruleId, String version, long expiresAt) implements Serializable {

        private static final long serialVersionUID = 4480221412440964909L;
    }
}
