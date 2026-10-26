package com.amoylabs.sfe4j.spring.boot.config;

import com.amoylabs.sfe4j.core.access.FileAccessService;
import com.amoylabs.sfe4j.core.configuration.Sfe4jConfiguration;
import com.amoylabs.sfe4j.core.service.FileExplorerService;
import com.amoylabs.sfe4j.spring.boot.access.CsrfSupport;
import com.amoylabs.sfe4j.spring.boot.access.DirectoryAuthorizationService;
import com.amoylabs.sfe4j.spring.boot.access.PasswordAttemptLimiter;
import com.amoylabs.sfe4j.spring.boot.access.SessionGrantStore;
import com.amoylabs.sfe4j.spring.boot.web.DownloadConcurrencyLimiter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the core access facade and the web level helpers.
 *
 * <p>{@link FileAccessService} is the only component able to reach the filesystem; controllers depend on
 * {@link FileExplorerService}, which always delegates to it.</p>
 */
@Configuration
public class BeanConfig {

    @Bean
    public FileAccessService initFileAccessService(Sfe4jConfiguration sfe4jConfiguration) {
        return new FileAccessService(sfe4jConfiguration);
    }

    @Bean
    public FileExplorerService initFileExplorerService(Sfe4jConfiguration sfe4jConfiguration,
                                                       FileAccessService fileAccessService) {
        FileExplorerService fileExplorerService = new FileExplorerService();
        fileExplorerService.setSfe4jConfiguration(sfe4jConfiguration);
        fileExplorerService.setFileAccessService(fileAccessService);
        return fileExplorerService;
    }

    @Bean
    public SessionGrantStore initSessionGrantStore(Sfe4jConfiguration sfe4jConfiguration,
                                                   FileAccessService fileAccessService) {
        return new SessionGrantStore(sfe4jConfiguration, fileAccessService.getProtectionRegistry());
    }

    @Bean(destroyMethod = "close")
    public PasswordAttemptLimiter initPasswordAttemptLimiter(Sfe4jConfiguration sfe4jConfiguration) {
        return new PasswordAttemptLimiter(sfe4jConfiguration.getPasswordAttemptsPerMinute());
    }

    @Bean
    public CsrfSupport initCsrfSupport() {
        return new CsrfSupport();
    }

    @Bean
    public DirectoryAuthorizationService initDirectoryAuthorizationService(FileAccessService fileAccessService,
                                                                           SessionGrantStore sessionGrantStore,
                                                                           PasswordAttemptLimiter passwordAttemptLimiter) {
        return new DirectoryAuthorizationService(fileAccessService, sessionGrantStore, passwordAttemptLimiter);
    }

    @Bean
    public DownloadConcurrencyLimiter initDownloadConcurrencyLimiter(Sfe4jConfiguration sfe4jConfiguration) {
        return new DownloadConcurrencyLimiter(sfe4jConfiguration.getDownloadMaxConcurrent());
    }
}
