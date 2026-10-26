package com.amoylabs.sfe4j.core.access;

import com.amoylabs.sfe4j.core.configuration.Sfe4jConfiguration;
import com.amoylabs.sfe4j.core.exception.FileAccessErrorCode;
import com.amoylabs.sfe4j.core.exception.FileAccessException;
import com.amoylabs.sfe4j.core.exception.Sfe4jRuntimeException;
import com.amoylabs.sfe4j.core.service.TextFileReader;
import com.amoylabs.sfe4j.core.util.PathStrings;
import com.amoylabs.sfe4j.core.vo.BreadcrumbVO;
import com.amoylabs.sfe4j.core.vo.FileContentVO;
import com.amoylabs.sfe4j.core.vo.FileDownloadVO;
import com.amoylabs.sfe4j.core.vo.FileTreeVO;
import com.amoylabs.sfe4j.core.vo.FileVO;
import com.amoylabs.sfe4j.core.vo.TextPreviewVO;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Single entry point for every filesystem access.
 *
 * <p>Listing, preview and download all go through {@link #authorize(String, FileOperation, FileAccessContext)}:
 * path normalization, base directory boundaries, symbolic link policy, directory passwords and target
 * type checks are applied in the same order. A caller must not build a {@code File} from a raw request
 * parameter and read it directly.</p>
 */
public class FileAccessService {

    private final Sfe4jConfiguration configuration;

    private final PathResolver pathResolver;

    private final FileAccessPolicy fileAccessPolicy = new FileAccessPolicy();

    private final TextFileReader textFileReader = new TextFileReader();

    public FileAccessService(Sfe4jConfiguration configuration) {
        if (configuration == null) {
            throw new Sfe4jRuntimeException("Sfe4jConfiguration must not be null.");
        }
        configuration.validate();
        this.configuration = configuration;
        this.pathResolver = new PathResolver(configuration);
    }

    public Sfe4jConfiguration getConfiguration() {
        return configuration;
    }

    public PathResolver getPathResolver() {
        return pathResolver;
    }

    public DirectoryProtectionRegistry getProtectionRegistry() {
        return pathResolver.getRegistry();
    }

    /**
     * Resolves and authorizes a request.
     *
     * <p>Directory passwords are checked against the lexical path before the filesystem is touched, so
     * an unauthorized caller cannot distinguish existing from missing targets. The real path is then
     * resolved and checked again, so an alias cannot bypass a rule.</p>
     *
     * @throws FileAccessException with the stable error code of the rejection reason
     */
    public ResolvedPath authorize(String requested, FileOperation operation, FileAccessContext context) {
        Path lexicalPath = pathResolver.normalizeRequest(requested);
        pathResolver.assertInsideLexicalRoot(lexicalPath);

        Set<DirectoryProtectionRule> rules = new LinkedHashSet<>(pathResolver.matchLexical(lexicalPath));
        fileAccessPolicy.assertAuthorized(rules, context);

        Path realPath = pathResolver.toRealPath(lexicalPath);
        pathResolver.assertInsideRealRoot(realPath);

        rules.addAll(pathResolver.matchReal(realPath));
        fileAccessPolicy.assertAuthorized(rules, context);

        pathResolver.assertSymbolicLinkPolicy(lexicalPath, realPath);
        fileAccessPolicy.assertOperationAllowed(realPath, operation);

        return new ResolvedPath(lexicalPath, realPath, new ArrayList<>(rules));
    }

    /**
     * Lists a directory.
     */
    public FileTreeVO list(String dir, FileAccessContext context) {
        ResolvedPath resolved = authorize(dir, FileOperation.LIST, context);
        Path lexicalPath = resolved.getLexicalPath();
        Path realPath = resolved.getRealPath();

        Set<FileVO> childDirectories = new TreeSet<>();
        Set<FileVO> files = new TreeSet<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(realPath)) {
            for (Path entry : stream) {
                Path lexicalChild = lexicalPath.resolve(entry.getFileName().toString());
                FileVO fileVO = new FileVO(lexicalChild.toFile());
                if (Files.isDirectory(entry)) {
                    childDirectories.add(fileVO);
                } else {
                    files.add(fileVO);
                }
            }
        } catch (AccessDeniedException e) {
            throw new FileAccessException(FileAccessErrorCode.ACCESS_DENIED,
                    "The requested directory is not readable.", e);
        } catch (IOException e) {
            throw new FileAccessException(FileAccessErrorCode.FILE_IO_ERROR,
                    "The requested directory cannot be read.", e);
        }

        FileTreeVO fileTree = new FileTreeVO();
        fileTree.setCurrentDirectory(new FileVO(lexicalPath.toFile()));
        fileTree.setParentDirectory(parentOf(lexicalPath));
        fileTree.setChildDirectories(childDirectories);
        fileTree.setFiles(files);
        fileTree.setBreadcrumbs(breadcrumbs(lexicalPath));
        return fileTree;
    }

    private FileVO parentOf(Path lexicalPath) {
        Path parent = lexicalPath.getParent();
        if (parent == null) {
            return null;
        }
        if (pathResolver.isRestrictToBaseDir() && !parent.startsWith(pathResolver.getBaseDir())) {
            return null;
        }
        return new FileVO(parent.toFile());
    }

    private List<BreadcrumbVO> breadcrumbs(Path lexicalPath) {
        List<Path> chain = new ArrayList<>();
        Path cursor = lexicalPath;
        while (cursor != null) {
            chain.add(cursor);
            cursor = cursor.getParent();
        }
        Collections.reverse(chain);

        List<BreadcrumbVO> breadcrumbs = new ArrayList<>(chain.size());
        Path baseDir = pathResolver.getBaseDir();
        for (Path path : chain) {
            if (pathResolver.isRestrictToBaseDir() && !path.startsWith(baseDir)) {
                continue;
            }
            boolean current = path.equals(lexicalPath);
            breadcrumbs.add(new BreadcrumbVO(PathStrings.displayName(path), PathStrings.toUrlPath(path), current));
        }
        if (breadcrumbs.isEmpty()) {
            breadcrumbs.add(new BreadcrumbVO(PathStrings.displayName(lexicalPath),
                    PathStrings.toUrlPath(lexicalPath), true));
        }
        return breadcrumbs;
    }

    /**
     * Reads a bounded plain text preview.
     */
    public TextPreviewVO preview(String file, FileAccessContext context) {
        ResolvedPath resolved = authorize(file, FileOperation.PREVIEW, context);
        Path lexicalPath = resolved.getLexicalPath();
        Path parent = lexicalPath.getParent();

        TextPreviewVO preview = textFileReader.read(resolved.getRealPath(), PathStrings.toUrlPath(lexicalPath),
                PathStrings.displayName(lexicalPath), configuration.getPreviewMaxBytes());
        preview.setParentPath(parent == null ? null : PathStrings.toUrlPath(parent));
        return preview;
    }

    /**
     * Authorizes a download and returns the target. The file is not opened here: the caller owns the
     * stream lifetime.
     */
    public FileDownloadVO openDownload(String file, FileAccessContext context) {
        ResolvedPath resolved = authorize(file, FileOperation.DOWNLOAD, context);
        Path realPath = resolved.getRealPath();

        long length;
        try {
            length = Files.size(realPath);
        } catch (IOException e) {
            throw new FileAccessException(FileAccessErrorCode.FILE_IO_ERROR, "The requested file cannot be read.", e);
        }

        String fileName = PathStrings.sanitizeHeaderValue(PathStrings.displayName(resolved.getLexicalPath()));
        return new FileDownloadVO(realPath, fileName, length);
    }

    /**
     * Bounded byte read kept for the legacy {@code readFile} entry point.
     *
     * @deprecated use {@link #preview(String, FileAccessContext)} or {@link #openDownload(String, FileAccessContext)}
     */
    @Deprecated(since = "1.1.0")
    public FileContentVO read(String file, FileAccessContext context) {
        ResolvedPath resolved = authorize(file, FileOperation.PREVIEW, context);

        int limit = (int) Math.min(configuration.getPreviewMaxBytes(), Integer.MAX_VALUE - 1L);
        byte[] content;
        try (InputStream inputStream = Files.newInputStream(resolved.getRealPath())) {
            content = inputStream.readNBytes(limit);
        } catch (IOException e) {
            throw new FileAccessException(FileAccessErrorCode.FILE_IO_ERROR, "The requested file cannot be read.", e);
        }

        FileContentVO fileContent = new FileContentVO();
        fileContent.setFileName(PathStrings.displayName(resolved.getLexicalPath()));
        fileContent.setContent(content);
        return fileContent;
    }

    /**
     * Rules protecting the given path, used by the web layer to decide whether a password form is
     * needed.
     *
     * <p>Deliberately lenient: it does not enforce boundaries and does not fail when the target is
     * missing, because the challenge must be shown before the caller learns anything about existence.</p>
     */
    public List<DirectoryProtectionRule> rulesProtecting(String requested) {
        if (pathResolver.getRegistry().isEmpty()) {
            return Collections.emptyList();
        }
        Path lexicalPath;
        try {
            lexicalPath = pathResolver.normalizeRequest(requested);
        } catch (FileAccessException e) {
            return Collections.emptyList();
        }

        Set<DirectoryProtectionRule> rules = new LinkedHashSet<>(pathResolver.matchLexical(lexicalPath));
        try {
            Path realPath = lexicalPath.toRealPath();
            rules.addAll(pathResolver.matchReal(realPath));
        } catch (IOException ignored) {
            // A missing or unresolvable target is handled by the facade; the lexical match is enough here.
        }
        return new ArrayList<>(rules);
    }
}
