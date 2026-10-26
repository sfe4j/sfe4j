package com.amoylabs.sfe4j.core.access;

import com.amoylabs.sfe4j.core.configuration.Sfe4jConfiguration;
import com.amoylabs.sfe4j.core.exception.FileAccessErrorCode;
import com.amoylabs.sfe4j.core.exception.FileAccessException;
import com.amoylabs.sfe4j.core.exception.Sfe4jRuntimeException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

/**
 * Turns a request parameter into a validated pair of lexical and real paths.
 *
 * <p>Rules implemented here:</p>
 * <ul>
 *   <li>the legacy absolute path form is preserved; a relative path is always resolved against the
 *       configured base directory and never against the process working directory;</li>
 *   <li>membership is decided with {@link Path#startsWith(Path)} on normalized paths, so {@code /data2}
 *       is not inside {@code /data};</li>
 *   <li>both the lexical path and the real path must stay inside the base directory when the
 *       restriction is enabled;</li>
 *   <li>symbolic links are rejected by default, including intermediate links, not only the final
 *       element.</li>
 * </ul>
 *
 * <p>Known limitation: there is still a time-of-check to time-of-use window between resolution and the
 * open of the target. Deployment must guarantee that untrusted users cannot modify the directory
 * structure, or must mount the data read-only.</p>
 */
public final class PathResolver {

    private static final int MAX_PATH_LENGTH = 4096;

    private static final int MAX_SEGMENTS = 128;

    private final Path baseDir;

    private final Path realBaseDir;

    private final boolean restrictToBaseDir;

    private final SymbolicLinkPolicy symbolicLinkPolicy;

    private final DirectoryProtectionRegistry registry;

    public PathResolver(Sfe4jConfiguration configuration) {
        this.restrictToBaseDir = configuration.getRestrictToBaseDir();
        this.symbolicLinkPolicy = configuration.getSymbolicLinkPolicy();

        String configuredBase = configuration.getBaseDirPath();
        Path candidate;
        try {
            candidate = Path.of(configuredBase);
        } catch (InvalidPathException e) {
            throw new Sfe4jRuntimeException("Illegal sfe4j.base-dir-path: '" + configuredBase + "'.", e);
        }

        if (!candidate.isAbsolute()) {
            throw new Sfe4jRuntimeException(
                    "sfe4j.base-dir-path must be an absolute path but was '" + configuredBase + "'.");
        }

        this.baseDir = candidate.normalize();

        if (!Files.exists(this.baseDir)) {
            throw new Sfe4jRuntimeException("sfe4j.base-dir-path does not exist: '" + this.baseDir + "'.");
        }
        if (!Files.isDirectory(this.baseDir)) {
            throw new Sfe4jRuntimeException("sfe4j.base-dir-path is not a directory: '" + this.baseDir + "'.");
        }

        try {
            this.realBaseDir = this.baseDir.toRealPath();
        } catch (IOException e) {
            throw new Sfe4jRuntimeException("sfe4j.base-dir-path cannot be resolved: '" + this.baseDir + "'.", e);
        }

        this.registry = DirectoryProtectionRegistry.build(configuration.getFolderPasswords(), this.baseDir);
    }

    public Path getBaseDir() {
        return baseDir;
    }

    public Path getRealBaseDir() {
        return realBaseDir;
    }

    public boolean isRestrictToBaseDir() {
        return restrictToBaseDir;
    }

    public SymbolicLinkPolicy getSymbolicLinkPolicy() {
        return symbolicLinkPolicy;
    }

    public DirectoryProtectionRegistry getRegistry() {
        return registry;
    }

    /**
     * Normalizes the request parameter.
     *
     * @param requested raw request value, {@code null} or blank means the base directory
     * @return normalized absolute path, not yet checked against the base directory
     * @throws FileAccessException {@link FileAccessErrorCode#INVALID_REQUEST} for illegal input
     */
    public Path normalizeRequest(String requested) {
        if (requested == null || requested.trim().isEmpty()) {
            return baseDir;
        }
        if (requested.length() > MAX_PATH_LENGTH) {
            throw new FileAccessException(FileAccessErrorCode.INVALID_REQUEST, "The path parameter is too long.");
        }
        for (int i = 0; i < requested.length(); i++) {
            char c = requested.charAt(i);
            if (c == '\0' || (c < 0x20 && c != '\t') || c == 0x7F) {
                throw new FileAccessException(FileAccessErrorCode.INVALID_REQUEST,
                        "The path parameter contains an illegal control character.");
            }
        }

        Path path;
        try {
            path = Path.of(requested);
        } catch (InvalidPathException e) {
            throw new FileAccessException(FileAccessErrorCode.INVALID_REQUEST, "The path parameter is not a legal path.");
        }

        if (!path.isAbsolute()) {
            path = baseDir.resolve(path);
        }
        path = path.normalize();

        if (path.getNameCount() > MAX_SEGMENTS) {
            throw new FileAccessException(FileAccessErrorCode.INVALID_REQUEST, "The path parameter is too deep.");
        }
        return path;
    }

    /**
     * Rejects a lexical path outside of the base directory.
     */
    public void assertInsideLexicalRoot(Path lexicalPath) {
        if (!restrictToBaseDir) {
            return;
        }
        if (!lexicalPath.startsWith(baseDir)) {
            throw new FileAccessException(FileAccessErrorCode.ACCESS_DENIED,
                    "The requested path is outside of the accessible area.");
        }
    }

    /**
     * Resolves the real path of an existing target.
     */
    public Path toRealPath(Path lexicalPath) {
        try {
            return lexicalPath.toRealPath();
        } catch (NoSuchFileException e) {
            throw new FileAccessException(FileAccessErrorCode.FILE_NOT_FOUND, "The requested path does not exist.", e);
        } catch (IOException e) {
            throw new FileAccessException(FileAccessErrorCode.FILE_IO_ERROR, "The requested path cannot be read.", e);
        }
    }

    /**
     * Rejects a real path outside of the real base directory.
     */
    public void assertInsideRealRoot(Path realPath) {
        if (!restrictToBaseDir) {
            return;
        }
        if (!realPath.startsWith(realBaseDir)) {
            throw new FileAccessException(FileAccessErrorCode.ACCESS_DENIED,
                    "The requested path is outside of the accessible area.");
        }
    }

    /**
     * Applies {@code sfe4j.access.symbolic-link-policy}.
     *
     * <p>{@link SymbolicLinkPolicy#DENY} inspects every segment between the effective root and the
     * target, so an intermediate link cannot be used to escape. {@link SymbolicLinkPolicy#WITHIN_ROOT}
     * accepts links as long as the real path check already succeeded.</p>
     */
    public void assertSymbolicLinkPolicy(Path lexicalPath, Path realPath) {
        if (symbolicLinkPolicy != SymbolicLinkPolicy.DENY) {
            return;
        }

        Path start = lexicalPath.startsWith(baseDir) ? baseDir : lexicalPath.getRoot();
        if (start == null) {
            throw new FileAccessException(FileAccessErrorCode.INVALID_REQUEST, "The path parameter is not a legal path.");
        }

        Path current = start;
        for (Path segment : start.relativize(lexicalPath)) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) {
                throw new FileAccessException(FileAccessErrorCode.ACCESS_DENIED,
                        "Symbolic links are not allowed by the configured policy.");
            }
        }
    }

    /**
     * Rules matching the lexical path.
     */
    public java.util.List<DirectoryProtectionRule> matchLexical(Path lexicalPath) {
        return registry.longestMatches(lexicalPath);
    }

    /**
     * Rules matching the real path.
     */
    public java.util.List<DirectoryProtectionRule> matchReal(Path realPath) {
        return registry.longestRealMatches(realPath);
    }
}
