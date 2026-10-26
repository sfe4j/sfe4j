package com.amoylabs.sfe4j.core.access;

import com.amoylabs.sfe4j.core.configuration.Sfe4jConfiguration;
import com.amoylabs.sfe4j.core.exception.FileAccessErrorCode;
import com.amoylabs.sfe4j.core.exception.FileAccessException;
import com.amoylabs.sfe4j.core.exception.Sfe4jRuntimeException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathResolverTest {

    @TempDir
    Path root;

    private static Sfe4jConfiguration configuration(Path baseDir, boolean restrict, SymbolicLinkPolicy policy) {
        Sfe4jConfiguration configuration = new Sfe4jConfiguration();
        configuration.setBaseDirPath(baseDir.toString());
        configuration.setRestrictToBaseDir(restrict);
        configuration.setSymbolicLinkPolicy(policy);
        return configuration;
    }

    @Test
    void blankRequestResolvesToBaseDirectory() {
        PathResolver resolver = new PathResolver(configuration(root, true, SymbolicLinkPolicy.DENY));

        assertEquals(root.normalize(), resolver.normalizeRequest(null));
        assertEquals(root.normalize(), resolver.normalizeRequest("   "));
    }

    @Test
    void relativePathIsResolvedAgainstBaseDirectory() throws IOException {
        Files.createDirectories(root.resolve("sub"));
        PathResolver resolver = new PathResolver(configuration(root, true, SymbolicLinkPolicy.DENY));

        Path resolved = resolver.normalizeRequest("sub");

        assertEquals(root.resolve("sub").normalize(), resolved);
    }

    @Test
    void absolutePathInsideBaseDirectoryIsKept() throws IOException {
        Path sub = Files.createDirectory(root.resolve("sub"));
        PathResolver resolver = new PathResolver(configuration(root, true, SymbolicLinkPolicy.DENY));

        assertEquals(sub.normalize(), resolver.normalizeRequest(sub.toString()));
    }

    @Test
    void parentTraversalIsRejected() {
        PathResolver resolver = new PathResolver(configuration(root, true, SymbolicLinkPolicy.DENY));

        Path traversal = resolver.normalizeRequest("..");

        FileAccessException exception = assertThrows(FileAccessException.class,
                () -> resolver.assertInsideLexicalRoot(traversal));
        assertEquals(FileAccessErrorCode.ACCESS_DENIED, exception.getCode());
    }

    @Test
    void siblingDirectorySharingTheNamePrefixIsNotInside() throws IOException {
        Path base = Files.createDirectory(root.resolve("data"));
        Path sibling = Files.createDirectory(root.resolve("data2"));
        PathResolver resolver = new PathResolver(configuration(base, true, SymbolicLinkPolicy.DENY));

        FileAccessException exception = assertThrows(FileAccessException.class,
                () -> resolver.assertInsideLexicalRoot(sibling.normalize()));
        assertEquals(FileAccessErrorCode.ACCESS_DENIED, exception.getCode());
    }

    @Test
    void controlCharactersAndNullBytesAreRejected() {
        PathResolver resolver = new PathResolver(configuration(root, true, SymbolicLinkPolicy.DENY));

        assertEquals(FileAccessErrorCode.INVALID_REQUEST,
                assertThrows(FileAccessException.class, () -> resolver.normalizeRequest("a\u0000b")).getCode());
        assertEquals(FileAccessErrorCode.INVALID_REQUEST,
                assertThrows(FileAccessException.class, () -> resolver.normalizeRequest("a\nb")).getCode());
    }

    @Test
    void tooLongPathIsRejected() {
        PathResolver resolver = new PathResolver(configuration(root, true, SymbolicLinkPolicy.DENY));

        assertEquals(FileAccessErrorCode.INVALID_REQUEST,
                assertThrows(FileAccessException.class, () -> resolver.normalizeRequest("a".repeat(5000))).getCode());
    }

    @Test
    void missingBaseDirectoryFailsAtStartup() {
        Sfe4jConfiguration configuration = configuration(root.resolve("missing"), true, SymbolicLinkPolicy.DENY);

        assertThrows(Sfe4jRuntimeException.class, () -> new PathResolver(configuration));
    }

    @Test
    void relativeBaseDirectoryFailsAtStartup() {
        Sfe4jConfiguration configuration = new Sfe4jConfiguration();
        configuration.setBaseDirPath("data");

        assertThrows(Sfe4jRuntimeException.class, () -> new PathResolver(configuration));
    }

    @Test
    void defaultPolicyIsDenyAndDefaultRestrictionIsOff() {
        Sfe4jConfiguration configuration = new Sfe4jConfiguration();
        configuration.setBaseDirPath(root.toString());

        PathResolver resolver = new PathResolver(configuration);

        assertEquals(SymbolicLinkPolicy.DENY, resolver.getSymbolicLinkPolicy());
        assertTrue(!resolver.isRestrictToBaseDir());
    }
}
