package com.amoylabs.sfe4j.core.access;

import com.amoylabs.sfe4j.core.exception.Sfe4jRuntimeException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * A protected directory declared through {@code sfe4j.folder-passwords}.
 *
 * <p>The rule keeps both the normalized lexical path (used to match the request before touching the
 * filesystem) and the real path (used to match a request that reached the same directory through an
 * alias). The password itself is never logged and never leaves the server: the session only keeps
 * the rule id together with {@link #getVersion()}.</p>
 */
public final class DirectoryProtectionRule {

    private final String id;

    private final String version;

    private final Path path;

    private final Path realPath;

    private final String password;

    private DirectoryProtectionRule(Path path, Path realPath, String password) {
        this.path = path;
        this.realPath = realPath;
        this.password = password;
        this.id = path.toString();
        this.version = versionOf(path, password);
    }

    /**
     * Creates a rule. The caller is responsible for having validated that {@code path} exists and is
     * a directory.
     */
    public static DirectoryProtectionRule create(Path path, Path realPath, String password) {
        if (path == null || realPath == null) {
            throw new Sfe4jRuntimeException("Protected directory path must be resolved before creating a rule.");
        }
        if (password == null || password.isEmpty()) {
            throw new Sfe4jRuntimeException(
                    "Password of protected directory '" + path + "' must not be empty (sfe4j.folder-passwords).");
        }
        return new DirectoryProtectionRule(path, realPath, password);
    }

    private static String versionOf(Path path, String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(path.toString().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(password.getBytes(StandardCharsets.UTF_8));
            byte[] hash = digest.digest();
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(Character.forDigit((b >> 4) & 0xF, 16));
                builder.append(Character.forDigit(b & 0xF, 16));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new Sfe4jRuntimeException("SHA-256 is not available.", e);
        }
    }

    /**
     * Stable identifier of the rule: the normalized lexical path of the protected directory.
     */
    public String getId() {
        return id;
    }

    /**
     * Opaque version derived from the path and the password. A configuration change invalidates every
     * grant created with the previous version.
     */
    public String getVersion() {
        return version;
    }

    public Path getPath() {
        return path;
    }

    public Path getRealPath() {
        return realPath;
    }

    public String getPassword() {
        return password;
    }

    /**
     * Number of path segments, used to select the longest match.
     */
    public int getDepth() {
        return path.getNameCount();
    }

    @Override
    public String toString() {
        return "DirectoryProtectionRule{id='" + id + "'}";
    }
}
