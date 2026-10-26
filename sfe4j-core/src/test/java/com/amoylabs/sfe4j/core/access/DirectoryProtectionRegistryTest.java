package com.amoylabs.sfe4j.core.access;

import com.amoylabs.sfe4j.core.exception.Sfe4jRuntimeException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DirectoryProtectionRegistryTest {

    @TempDir
    Path root;

    @Test
    void missingProtectedDirectoryFailsAtStartup() {
        Map<String, String> passwords = Map.of(root.resolve("missing").toString(), "pwd");

        assertThrows(Sfe4jRuntimeException.class, () -> DirectoryProtectionRegistry.build(passwords, root));
    }

    @Test
    void protectedPathMustBeADirectory() throws IOException {
        Path file = Files.writeString(root.resolve("file.txt"), "x");

        assertThrows(Sfe4jRuntimeException.class,
                () -> DirectoryProtectionRegistry.build(Map.of(file.toString(), "pwd"), root));
    }

    @Test
    void emptyPasswordFailsAtStartup() throws IOException {
        Path directory = Files.createDirectory(root.resolve("secret"));

        assertThrows(Sfe4jRuntimeException.class,
                () -> DirectoryProtectionRegistry.build(Map.of(directory.toString(), ""), root));
    }

    @Test
    void relativeProtectedPathIsResolvedAgainstTheBaseDirectory() throws IOException {
        Files.createDirectory(root.resolve("secret"));

        DirectoryProtectionRegistry registry = DirectoryProtectionRegistry.build(Map.of("secret", "pwd"), root);

        assertEquals(1, registry.getRules().size());
        assertEquals(root.resolve("secret").normalize().toString(), registry.getRules().get(0).getId());
    }

    @Test
    void duplicatedPathAfterNormalizationFailsAtStartup() throws IOException {
        Files.createDirectory(root.resolve("secret"));
        String absolute = root.resolve("secret").toString();

        Map<String, String> passwords = new LinkedHashMap<>();
        passwords.put(absolute, "one");
        passwords.put(absolute + "/.", "two");

        assertThrows(Sfe4jRuntimeException.class, () -> DirectoryProtectionRegistry.build(passwords, root));
    }

    @Test
    void longestMatchIsSelectedPerPathSpace() throws IOException {
        Path outer = Files.createDirectories(root.resolve("outer"));
        Path inner = Files.createDirectories(outer.resolve("inner"));

        Map<String, String> passwords = new LinkedHashMap<>();
        passwords.put(outer.toString(), "outer-pwd");
        passwords.put(inner.toString(), "inner-pwd");
        DirectoryProtectionRegistry registry = DirectoryProtectionRegistry.build(passwords, root);

        List<DirectoryProtectionRule> innerMatches = registry.longestMatches(inner.resolve("file.txt"));
        assertEquals(1, innerMatches.size());
        assertEquals(inner.normalize().toString(), innerMatches.get(0).getId());

        List<DirectoryProtectionRule> outerMatches = registry.longestMatches(outer.resolve("other.txt"));
        assertEquals(1, outerMatches.size());
        assertEquals(outer.normalize().toString(), outerMatches.get(0).getId());
    }

    @Test
    void matchingBothSpacesReturnsBothRules() throws IOException {
        Path protectedDir = Files.createDirectory(root.resolve("secret"));
        DirectoryProtectionRegistry registry = DirectoryProtectionRegistry.build(
                Map.of(protectedDir.toString(), "pwd"), root);

        List<DirectoryProtectionRule> rules = registry.match(protectedDir.resolve("file.txt"),
                protectedDir.toRealPath().resolve("file.txt"));

        assertEquals(1, rules.size(), "the same rule matched twice is returned once");
    }

    @Test
    void ruleVersionChangesWithThePassword() throws IOException {
        Path protectedDir = Files.createDirectory(root.resolve("secret"));

        String first = DirectoryProtectionRegistry.build(Map.of(protectedDir.toString(), "one"), root)
                .getRules().get(0).getVersion();
        String second = DirectoryProtectionRegistry.build(Map.of(protectedDir.toString(), "two"), root)
                .getRules().get(0).getVersion();

        assertTrue(!first.equals(second));
    }

    @Test
    void emptyConfigurationProducesAnEmptyRegistry() {
        assertTrue(DirectoryProtectionRegistry.build(null, root).isEmpty());
        assertTrue(DirectoryProtectionRegistry.build(Map.of(), root).isEmpty());
    }
}
