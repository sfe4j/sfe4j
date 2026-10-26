package com.amoylabs.sfe4j.core.access;

import com.amoylabs.sfe4j.core.exception.FileAccessErrorCode;
import com.amoylabs.sfe4j.core.exception.FileAccessException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;

/**
 * Applies the access rules for a resolved target.
 *
 * <p>Authorization runs before existence checks whenever possible, so an unauthorized caller cannot
 * use the difference between 403 and 404 to probe the filesystem.</p>
 */
public final class FileAccessPolicy {

    /**
     * Requires a valid grant for every matching protection rule.
     */
    public void assertAuthorized(Collection<DirectoryProtectionRule> rules, FileAccessContext context) {
        if (rules == null || rules.isEmpty()) {
            return;
        }
        FileAccessContext effectiveContext = context == null ? FileAccessContext.anonymous() : context;
        for (DirectoryProtectionRule rule : rules) {
            if (!effectiveContext.hasGrant(rule)) {
                throw new FileAccessException(FileAccessErrorCode.DIRECTORY_AUTH_REQUIRED,
                        "A directory password is required to access this target.");
            }
        }
    }

    /**
     * Checks target type and readability for the requested operation.
     *
     * @param realPath fully resolved path
     */
    public void assertOperationAllowed(Path realPath, FileOperation operation) {
        BasicFileAttributes attributes;
        try {
            attributes = Files.readAttributes(realPath, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (NoSuchFileException e) {
            throw new FileAccessException(FileAccessErrorCode.FILE_NOT_FOUND, "The requested path does not exist.", e);
        } catch (IOException e) {
            throw new FileAccessException(FileAccessErrorCode.FILE_IO_ERROR, "The requested path cannot be read.", e);
        }

        switch (operation) {
            case LIST:
                if (!attributes.isDirectory()) {
                    throw new FileAccessException(FileAccessErrorCode.INVALID_REQUEST,
                            "The requested path is not a directory.");
                }
                break;
            case PREVIEW:
                if (!attributes.isRegularFile()) {
                    throw new FileAccessException(FileAccessErrorCode.UNSUPPORTED_PREVIEW,
                            "Only regular files can be previewed.");
                }
                break;
            case DOWNLOAD:
                if (!attributes.isRegularFile()) {
                    throw new FileAccessException(FileAccessErrorCode.ACCESS_DENIED,
                            "Only regular files can be downloaded.");
                }
                break;
            default:
                throw new FileAccessException(FileAccessErrorCode.INVALID_REQUEST, "Unsupported operation.");
        }

        if (!Files.isReadable(realPath)) {
            throw new FileAccessException(FileAccessErrorCode.ACCESS_DENIED, "The requested path is not readable.");
        }
    }
}
