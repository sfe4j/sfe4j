package com.amoylabs.sfe4j.spring.boot;

import com.amoylabs.sfe4j.core.access.DirectoryProtectionRule;
import com.amoylabs.sfe4j.core.access.FileAccessService;
import com.amoylabs.sfe4j.core.configuration.Sfe4jConfiguration;
import com.amoylabs.sfe4j.core.service.FileExplorerService;
import com.amoylabs.sfe4j.spring.boot.access.CsrfSupport;
import com.amoylabs.sfe4j.spring.boot.access.DirectoryAuthorizationService;
import com.amoylabs.sfe4j.spring.boot.access.PasswordAttemptLimiter;
import com.amoylabs.sfe4j.spring.boot.access.SessionGrantStore;
import com.amoylabs.sfe4j.spring.boot.web.DownloadConcurrencyLimiter;
import com.amoylabs.sfe4j.spring.boot.web.Sfe4jExceptionHandler;

import org.springframework.mock.web.MockServletContext;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Real object graph backed by a temporary directory, shared by the web layer tests.
 */
public final class Sfe4jTestFixture implements AutoCloseable {

    public final Path root;

    public final Path publicDir;

    public final Path secretDir;

    public final Path secretChild;

    public final Sfe4jConfiguration configuration;

    public final FileAccessService fileAccessService;

    public final FileExplorerService fileExplorerService;

    public final SessionGrantStore grantStore;

    public final PasswordAttemptLimiter attemptLimiter;

    public final DirectoryAuthorizationService authorizationService;

    public final CsrfSupport csrfSupport = new CsrfSupport();

    public final DownloadConcurrencyLimiter downloadConcurrencyLimiter;

    public final MockServletContext servletContext = new MockServletContext();

    private Sfe4jTestFixture(Path root, String contextPath, int maxConcurrent, int passwordAttemptsPerMinute)
            throws IOException {
        this.root = root;
        this.publicDir = Files.createDirectories(root.resolve("public"));
        this.secretDir = Files.createDirectories(root.resolve("secret"));
        this.secretChild = Files.createDirectories(secretDir.resolve("inner"));
        Files.writeString(publicDir.resolve("note.txt"), "hello world", StandardCharsets.UTF_8);
        Files.writeString(publicDir.resolve("script.html"), "<script>alert('xss')</script>", StandardCharsets.UTF_8);
        Files.writeString(secretDir.resolve("hidden.txt"), "top secret", StandardCharsets.UTF_8);
        Files.writeString(secretChild.resolve("deep.txt"), "deep", StandardCharsets.UTF_8);

        this.configuration = new Sfe4jConfiguration();
        configuration.setBaseDirPath(root.toString());
        configuration.setRestrictToBaseDir(true);
        configuration.setFolderPasswords(Map.of(secretDir.toString(), "secret-pass"));
        configuration.setPasswordAttemptsPerMinute(passwordAttemptsPerMinute);
        configuration.setPreviewMaxBytes(1024L);

        this.fileAccessService = new FileAccessService(configuration);
        this.fileExplorerService = new FileExplorerService();
        fileExplorerService.setSfe4jConfiguration(configuration);
        fileExplorerService.setFileAccessService(fileAccessService);
        this.grantStore = new SessionGrantStore(configuration, fileAccessService.getProtectionRegistry());
        this.attemptLimiter = new PasswordAttemptLimiter(passwordAttemptsPerMinute);
        this.authorizationService = new DirectoryAuthorizationService(fileAccessService, grantStore, attemptLimiter);
        this.downloadConcurrencyLimiter = new DownloadConcurrencyLimiter(maxConcurrent);

        servletContext.setContextPath(contextPath);
    }

    public static Sfe4jTestFixture create(Path root) throws IOException {
        return new Sfe4jTestFixture(root, "", 8, 5);
    }

    public static Sfe4jTestFixture create(Path root, String contextPath, int maxConcurrent,
                                          int passwordAttemptsPerMinute) throws IOException {
        return new Sfe4jTestFixture(root, contextPath, maxConcurrent, passwordAttemptsPerMinute);
    }

    /**
     * Records a valid grant for the directory protecting the given path, as a successful password
     * submission would do.
     */
    public void grant(HttpSession session, Path protectedPath) {
        List<DirectoryProtectionRule> rules = fileAccessService.rulesProtecting(protectedPath.toString());
        grantStore.grant(session, rules);
    }

    public Sfe4jExceptionHandler exceptionHandler() {
        Sfe4jExceptionHandler handler = new Sfe4jExceptionHandler();
        handler.setFileExplorerService(fileExplorerService);
        return handler;
    }

    public static String csrfTokenFrom(ModelAndView modelAndView) {
        Object token = modelAndView.getModel().get("csrfToken");
        return token == null ? null : token.toString();
    }

    @Override
    public void close() {
        attemptLimiter.close();
    }
}
