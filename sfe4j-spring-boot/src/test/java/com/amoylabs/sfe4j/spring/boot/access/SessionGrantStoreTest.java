package com.amoylabs.sfe4j.spring.boot.access;

import com.amoylabs.sfe4j.core.access.DirectoryProtectionRule;
import com.amoylabs.sfe4j.core.access.FileAccessContext;
import com.amoylabs.sfe4j.core.access.FileAccessService;
import com.amoylabs.sfe4j.core.configuration.Sfe4jConfiguration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpSession;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionGrantStoreTest {

    @TempDir
    Path root;

    private Path secretDir;

    private Sfe4jConfiguration configuration;

    private FileAccessService fileAccessService;

    private MockHttpSession session;

    @BeforeEach
    void setUp() throws IOException {
        secretDir = Files.createDirectory(root.resolve("secret"));
        configuration = new Sfe4jConfiguration();
        configuration.setBaseDirPath(root.toString());
        configuration.setRestrictToBaseDir(true);
        configuration.setFolderPasswords(Map.of(secretDir.toString(), "pwd"));
        fileAccessService = new FileAccessService(configuration);
        session = new MockHttpSession();
    }

    @Test
    void grantIsValidUntilTheTimeToLiveExpires() {
        AtomicLong now = new AtomicLong(1_000_000L);
        SessionGrantStore store = new SessionGrantStore(configuration,
                fileAccessService.getProtectionRegistry(), now::get);
        DirectoryProtectionRule rule = rule();

        store.grant(session, List.of(rule));

        assertTrue(store.contextFor(session).hasGrant(rule));

        now.addAndGet(configuration.getGrantTtlSeconds() * 1000L - 1);
        assertTrue(store.contextFor(session).hasGrant(rule), "still valid one millisecond before expiry");

        now.addAndGet(1L);
        assertFalse(store.contextFor(session).hasGrant(rule), "expired grants must be dropped");
    }

    @Test
    void grantIsInvalidatedWhenTheRuleVersionChanges() throws IOException {
        SessionGrantStore store = new SessionGrantStore(configuration,
                fileAccessService.getProtectionRegistry(), System::currentTimeMillis);
        store.grant(session, List.of(rule()));

        Sfe4jConfiguration changed = new Sfe4jConfiguration();
        changed.setBaseDirPath(root.toString());
        changed.setRestrictToBaseDir(true);
        changed.setFolderPasswords(Map.of(secretDir.toString(), "other-password"));
        FileAccessService reconfigured = new FileAccessService(changed);
        SessionGrantStore restarted = new SessionGrantStore(changed,
                reconfigured.getProtectionRegistry(), System::currentTimeMillis);

        FileAccessContext context = restarted.contextFor(session);
        assertFalse(context.hasGrant(reconfigured.getProtectionRegistry().getRules().get(0)));
    }

    @Test
    void revokeRemovesOnlyTheSfe4jAttribute() {
        SessionGrantStore store = new SessionGrantStore(configuration,
                fileAccessService.getProtectionRegistry(), System::currentTimeMillis);
        session.setAttribute("host.attribute", "keep-me");
        store.grant(session, List.of(rule()));

        store.revoke(session);

        FileAccessContext context = store.contextFor(session);
        assertFalse(context.hasGrant(rule()));
        assertEquals("keep-me", session.getAttribute("host.attribute"));
        assertTrue(session.getId() != null && !session.getId().isEmpty(), "the host session stays alive");
    }

    @Test
    void anonymousContextIsReturnedWithoutSession() {
        SessionGrantStore store = new SessionGrantStore(configuration,
                fileAccessService.getProtectionRegistry(), System::currentTimeMillis);

        FileAccessContext context = store.contextFor(null);

        assertTrue(context.isAnonymous());
    }

    private DirectoryProtectionRule rule() {
        return fileAccessService.getProtectionRegistry().getRules().get(0);
    }
}
