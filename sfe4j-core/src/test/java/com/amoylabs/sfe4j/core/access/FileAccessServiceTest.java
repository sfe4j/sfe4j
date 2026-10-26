package com.amoylabs.sfe4j.core.access;

import com.amoylabs.sfe4j.core.configuration.Sfe4jConfiguration;
import com.amoylabs.sfe4j.core.exception.FileAccessErrorCode;
import com.amoylabs.sfe4j.core.exception.FileAccessException;
import com.amoylabs.sfe4j.core.vo.BreadcrumbVO;
import com.amoylabs.sfe4j.core.vo.FileDownloadVO;
import com.amoylabs.sfe4j.core.vo.FileTreeVO;
import com.amoylabs.sfe4j.core.vo.TextPreviewVO;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for the unified access facade: the three entry points must apply the same boundaries
 * and the same directory passwords.
 */
class FileAccessServiceTest {

    @TempDir
    Path root;

    private Path publicDir;

    private Path secretDir;

    private Path secretChild;

    private FileAccessService service;

    @BeforeEach
    void setUp() throws IOException {
        publicDir = Files.createDirectories(root.resolve("public"));
        secretDir = Files.createDirectories(root.resolve("secret"));
        secretChild = Files.createDirectories(secretDir.resolve("inner"));
        Files.writeString(publicDir.resolve("note.txt"), "hello");
        Files.writeString(secretDir.resolve("hidden.txt"), "top secret");
        Files.writeString(secretChild.resolve("deep.txt"), "deep");
        Files.writeString(root.resolve("root.txt"), "root");

        service = new FileAccessService(configuration(Map.of(secretDir.toString(), "outer-pass")));
    }

    private Sfe4jConfiguration configuration(Map<String, String> folderPasswords) {
        Sfe4jConfiguration configuration = new Sfe4jConfiguration();
        configuration.setBaseDirPath(root.toString());
        configuration.setRestrictToBaseDir(true);
        configuration.setFolderPasswords(folderPasswords);
        configuration.setPreviewMaxBytes(1024L);
        return configuration;
    }

    private FileAccessContext granted(FileAccessService target, Path protectedDir) {
        DirectoryProtectionRule rule = target.getProtectionRegistry().findById(protectedDir.normalize().toString());
        assertTrue(rule != null, "expected a protection rule for " + protectedDir);
        return FileAccessContext.of("tester", Map.of(rule.getId(), rule.getVersion()));
    }

    @Test
    void anonymousCannotListProtectedDirectory() {
        FileAccessException exception = assertThrows(FileAccessException.class,
                () -> service.list(secretDir.toString(), FileAccessContext.anonymous()));
        assertEquals(FileAccessErrorCode.DIRECTORY_AUTH_REQUIRED, exception.getCode());
    }

    @Test
    void anonymousCannotPreviewProtectedFile() {
        FileAccessException exception = assertThrows(FileAccessException.class,
                () -> service.preview(secretDir.resolve("hidden.txt").toString(), FileAccessContext.anonymous()));
        assertEquals(FileAccessErrorCode.DIRECTORY_AUTH_REQUIRED, exception.getCode());
    }

    @Test
    void anonymousCannotDownloadProtectedFile() {
        FileAccessException exception = assertThrows(FileAccessException.class,
                () -> service.openDownload(secretDir.resolve("hidden.txt").toString(), FileAccessContext.anonymous()));
        assertEquals(FileAccessErrorCode.DIRECTORY_AUTH_REQUIRED, exception.getCode());
    }

    @Test
    void authorizationIsCheckedBeforeExistence() {
        FileAccessException exception = assertThrows(FileAccessException.class,
                () -> service.preview(secretDir.resolve("missing.txt").toString(), FileAccessContext.anonymous()));
        assertEquals(FileAccessErrorCode.DIRECTORY_AUTH_REQUIRED, exception.getCode(),
                "an unauthorized caller must not learn whether the target exists");
    }

    @Test
    void grantCoversTheWholeProtectedSubtree() {
        FileAccessContext context = granted(service, secretDir);

        assertEquals(1, service.list(secretDir.toString(), context).getFiles().size());
        assertEquals("deep", service.preview(secretChild.resolve("deep.txt").toString(), context).getContent());
        assertEquals("top secret", service.preview(secretDir.resolve("hidden.txt").toString(), context).getContent());
    }

    @Test
    void grantOfAnotherRuleDoesNotUnlockTheDirectory() throws IOException {
        Path otherProtected = Files.createDirectory(root.resolve("other"));
        Sfe4jConfiguration configuration = configuration(Map.of(
                secretDir.toString(), "outer-pass",
                otherProtected.toString(), "other-pass"));
        FileAccessService configured = new FileAccessService(configuration);

        FileAccessContext context = granted(configured, otherProtected);

        assertEquals(FileAccessErrorCode.DIRECTORY_AUTH_REQUIRED, assertThrows(FileAccessException.class,
                () -> configured.list(secretDir.toString(), context)).getCode());
    }

    @Test
    void staleRuleVersionIsRejected() {
        FileAccessContext oldContext = granted(service, secretDir);

        Sfe4jConfiguration changed = configuration(Map.of(secretDir.toString(), "new-password"));
        FileAccessService reconfigured = new FileAccessService(changed);

        assertEquals(FileAccessErrorCode.DIRECTORY_AUTH_REQUIRED, assertThrows(FileAccessException.class,
                () -> reconfigured.list(secretDir.toString(), oldContext)).getCode());
    }

    @Test
    void longestMatchingRuleWinsForNestedDirectories() {
        Sfe4jConfiguration nested = configuration(Map.of(
                secretDir.toString(), "outer-pass",
                secretChild.toString(), "inner-pass"));
        FileAccessService configured = new FileAccessService(nested);

        FileAccessContext innerGrant = granted(configured, secretChild);

        assertFalse(configured.list(secretChild.toString(), innerGrant).getFiles().isEmpty());
        assertEquals(FileAccessErrorCode.DIRECTORY_AUTH_REQUIRED, assertThrows(FileAccessException.class,
                () -> configured.list(secretDir.toString(), innerGrant)).getCode(),
                "the outer directory keeps its own password");
    }

    @Test
    void outsideBaseDirectoryIsDenied() {
        Path outside = root.getParent();

        assertEquals(FileAccessErrorCode.ACCESS_DENIED, assertThrows(FileAccessException.class,
                () -> service.list(outside.toString(), FileAccessContext.anonymous())).getCode());
    }

    @Test
    void siblingDirectorySharingThePrefixIsDenied() throws IOException {
        Path base = Files.createDirectory(root.resolve("data"));
        Path sibling = Files.createDirectory(root.resolve("data2"));
        Sfe4jConfiguration configuration = configuration(Map.of());
        configuration.setBaseDirPath(base.toString());
        FileAccessService configured = new FileAccessService(configuration);

        assertEquals(FileAccessErrorCode.ACCESS_DENIED, assertThrows(FileAccessException.class,
                () -> configured.list(sibling.toString(), FileAccessContext.anonymous())).getCode());
    }

    @Test
    void missingTargetInsideTheAccessibleAreaReturnsNotFound() {
        assertEquals(FileAccessErrorCode.FILE_NOT_FOUND, assertThrows(FileAccessException.class,
                () -> service.list(publicDir.resolve("nope").toString(), FileAccessContext.anonymous())).getCode());
    }

    @Test
    void downloadRejectsDirectories() {
        assertEquals(FileAccessErrorCode.ACCESS_DENIED, assertThrows(FileAccessException.class,
                () -> service.openDownload(publicDir.toString(), FileAccessContext.anonymous())).getCode());
    }

    @Test
    void previewRejectsDirectories() {
        assertEquals(FileAccessErrorCode.UNSUPPORTED_PREVIEW, assertThrows(FileAccessException.class,
                () -> service.preview(publicDir.toString(), FileAccessContext.anonymous())).getCode());
    }

    @Test
    void downloadTargetKeepsTheOriginalName() {
        FileDownloadVO target = service.openDownload(publicDir.resolve("note.txt").toString(),
                FileAccessContext.anonymous());

        assertEquals("note.txt", target.getFileName());
        assertEquals(5L, target.getLength());
    }

    @Test
    void previewTruncatesLargeFilesWithoutSplittingMultiByteCharacters() throws IOException {
        String content = "a" + "é".repeat(1500);
        Path large = publicDir.resolve("large.txt");
        Files.writeString(large, content, StandardCharsets.UTF_8);

        TextPreviewVO preview = service.preview(large.toString(), FileAccessContext.anonymous());

        assertTrue(preview.isTruncated());
        assertFalse(preview.isDecodingIssue());
        assertFalse(preview.getContent().contains("\uFFFD"), "a truncated multi-byte character must not leak as garbage");
        assertEquals(1 + 511, preview.getContent().length());
        assertEquals(1024L, preview.getMaxBytes());
    }

    @Test
    void previewRejectsBinaryContent() throws IOException {
        Path binary = publicDir.resolve("binary.bin");
        Files.write(binary, new byte[]{'a', 0, 'b', 1, 2, 3});

        assertEquals(FileAccessErrorCode.UNSUPPORTED_PREVIEW, assertThrows(FileAccessException.class,
                () -> service.preview(binary.toString(), FileAccessContext.anonymous())).getCode());
    }

    @Test
    void legacyReadFileIsRejectedForProtectedFiles() {
        assertThrows(FileAccessException.class,
                () -> service.read(secretDir.resolve("hidden.txt").toString(), FileAccessContext.anonymous()));
    }

    @Test
    void breadcrumbsAccumulateSegmentsForRepeatedNames() throws IOException {
        Path repeated = Files.createDirectories(root.resolve("same").resolve("same"));

        FileTreeVO tree = service.list(repeated.toString(), FileAccessContext.anonymous());
        List<BreadcrumbVO> breadcrumbs = tree.getBreadcrumbs();

        assertEquals(3, breadcrumbs.size());
        assertEquals(root.normalize().toString(), breadcrumbs.get(0).getPath());
        assertEquals(root.resolve("same").normalize().toString(), breadcrumbs.get(1).getPath());
        assertEquals(repeated.normalize().toString(), breadcrumbs.get(2).getPath());
        assertTrue(breadcrumbs.get(2).isCurrent());
    }

    @Test
    void parentLinkIsHiddenAboveTheBaseDirectory() {
        FileTreeVO tree = service.list(root.toString(), FileAccessContext.anonymous());

        assertNull(tree.getParentDirectory());
    }

    @Test
    void rulesProtectingIsAvailableBeforeTheTargetExists() {
        List<DirectoryProtectionRule> rules = service.rulesProtecting(secretDir.resolve("missing.txt").toString());

        assertEquals(1, rules.size());
    }

    @Test
    void symbolicLinksAreDeniedByDefault() throws IOException {
        Path outsideFile = Files.writeString(root.getParent().resolve("outside-" + root.getFileName() + ".txt"), "x");
        Path link = publicDir.resolve("link.txt");
        assumeSymbolicLinksSupported(outsideFile, link);

        assertEquals(FileAccessErrorCode.ACCESS_DENIED, assertThrows(FileAccessException.class,
                () -> service.preview(link.toString(), FileAccessContext.anonymous())).getCode());
    }

    @Test
    void withinRootPolicyAllowsInternalLinksButNotEscapingLinks() throws IOException {
        Path insideTarget = Files.writeString(publicDir.resolve("real.txt"), "inside");
        Path internalLink = publicDir.resolve("internal-link.txt");
        assumeSymbolicLinksSupported(insideTarget, internalLink);

        Path outsideTarget = Files.writeString(root.getParent().resolve("escape-" + root.getFileName() + ".txt"), "x");
        Path escapingLink = publicDir.resolve("escaping-link.txt");
        assumeSymbolicLinksSupported(outsideTarget, escapingLink);

        Sfe4jConfiguration configuration = configuration(Map.of());
        configuration.setSymbolicLinkPolicy(SymbolicLinkPolicy.WITHIN_ROOT);
        FileAccessService permissive = new FileAccessService(configuration);

        assertEquals("inside", permissive.preview(internalLink.toString(), FileAccessContext.anonymous()).getContent());
        assertEquals(FileAccessErrorCode.ACCESS_DENIED, assertThrows(FileAccessException.class,
                () -> permissive.preview(escapingLink.toString(), FileAccessContext.anonymous())).getCode());
    }

    @Test
    void aliasPathCannotBypassDirectoryPassword() throws IOException {
        Path alias = root.resolve("alias");
        try {
            Files.createSymbolicLink(alias, secretDir);
        } catch (IOException | UnsupportedOperationException e) {
            Assumptions.abort("File system does not support symbolic links: " + e.getMessage());
        }

        Sfe4jConfiguration configuration = configuration(Map.of(secretDir.toString(), "outer-pass"));
        configuration.setSymbolicLinkPolicy(SymbolicLinkPolicy.WITHIN_ROOT);
        FileAccessService permissive = new FileAccessService(configuration);

        assertEquals(FileAccessErrorCode.DIRECTORY_AUTH_REQUIRED, assertThrows(FileAccessException.class,
                () -> permissive.preview(alias.resolve("hidden.txt").toString(), FileAccessContext.anonymous())).getCode(),
                "the real path must be matched against the protection rules");
    }

    private void assumeSymbolicLinksSupported(Path target, Path link) throws IOException {
        try {
            Files.createSymbolicLink(link, target);
        } catch (IOException | UnsupportedOperationException e) {
            Assumptions.abort("File system does not support symbolic links: " + e.getMessage());
        }
    }
}
