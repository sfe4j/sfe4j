package com.amoylabs.sfe4j.spring.boot.starter;

import com.amoylabs.sfe4j.core.access.SymbolicLinkPolicy;
import com.amoylabs.sfe4j.core.configuration.Sfe4jConfiguration;
import com.amoylabs.sfe4j.core.exception.Sfe4jRuntimeException;
import com.amoylabs.sfe4j.startertest.PropertiesOnlyConfiguration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.MockServletContext;

import jakarta.servlet.ServletContext;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies that the {@code sfe4j.*} properties reach the framework independent configuration and that
 * an out of range value fails the application instead of silently falling back to a default.
 */
class Sfe4jAutoConfigurationTest {

    private final ApplicationContextRunner propertiesRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesOnlyConfiguration.class);

    private final ApplicationContextRunner starterRunner = new ApplicationContextRunner()
            .withUserConfiguration(Sfe4jAutoConfiguration.class)
            .withBean(ServletContext.class, MockServletContext::new);

    @Test
    void starterBootstrapsTheWholeApplicationContext(@TempDir Path baseDir) {
        starterRunner
                .withPropertyValues("sfe4j.base-dir-path=" + baseDir)
                .run(context -> {
                    assertThat(context).hasSingleBean(Sfe4jConfiguration.class);
                    assertThat(context).hasSingleBean(
                            com.amoylabs.sfe4j.core.service.FileExplorerService.class);
                    assertThat(context).hasSingleBean(
                            com.amoylabs.sfe4j.core.access.FileAccessService.class);
                    assertThat(context).hasSingleBean(
                            com.amoylabs.sfe4j.spring.boot.controller.FileExplorerController.class);
                    assertThat(context).hasSingleBean(
                            com.amoylabs.sfe4j.spring.boot.controller.FileViewerController.class);
                    assertThat(context).hasSingleBean(
                            com.amoylabs.sfe4j.spring.boot.controller.FileDownloaderController.class);
                });
    }

    @Test
    void defaultsAreAppliedWhenNothingIsConfigured() {
        Sfe4jConfiguration configuration = bind();

        assertThat(configuration.getBaseDirPath()).isEqualTo("/");
        assertThat(configuration.getRestrictToBaseDir()).isFalse();
        assertThat(configuration.getSymbolicLinkPolicy()).isEqualTo(SymbolicLinkPolicy.DENY);
        assertThat(configuration.getGrantTtlSeconds()).isEqualTo(1800);
        assertThat(configuration.getPasswordAttemptsPerMinute()).isEqualTo(5);
        assertThat(configuration.getPreviewMaxBytes()).isEqualTo(1048576L);
        assertThat(configuration.getDownloadMaxConcurrent()).isEqualTo(8);
        assertThat(configuration.getLogsEnabled()).isFalse();
        assertThat(configuration.getLogsDefaultLines()).isEqualTo(200);
        assertThat(configuration.getLogsMaxLines()).isEqualTo(2000);
        assertThat(configuration.getLogsMaxScanBytes()).isEqualTo(1048576L);
        assertThat(configuration.getLogsPollIntervalMs()).isEqualTo(1000L);
        assertThat(configuration.getLogsMaxConnections()).isEqualTo(20);
        assertThat(configuration.getLogsMaxConnectionsPerSession()).isEqualTo(2);
        assertThat(configuration.getLogsMaxConnectionSeconds()).isEqualTo(1800L);
        assertThat(configuration.getLogsMaxQueueBytes()).isEqualTo(262144);
        assertThat(configuration.getLogsMaxChunkBytes()).isEqualTo(65536);
    }

    @Test
    void nestedSectionsAreBound() {
        Sfe4jConfiguration configuration = bind(
                "sfe4j.base-dir-path=/data",
                "sfe4j.restrict-to-base-dir=true",
                "sfe4j.title=Files",
                "sfe4j.description=demo",
                "sfe4j.access.symbolic-link-policy=within-root",
                "sfe4j.access.grant-ttl-seconds=900",
                "sfe4j.access.password-attempts-per-minute=3",
                "sfe4j.preview.max-bytes=65536",
                "sfe4j.download.max-concurrent=4",
                "sfe4j.logs.enabled=true",
                "sfe4j.logs.default-lines=100",
                "sfe4j.logs.max-lines=500",
                "sfe4j.logs.max-scan-bytes=65536",
                "sfe4j.logs.poll-interval-ms=2000",
                "sfe4j.logs.max-connections=5",
                "sfe4j.logs.max-connections-per-session=1",
                "sfe4j.logs.max-connection-seconds=600",
                "sfe4j.logs.max-queue-bytes=131072",
                "sfe4j.logs.max-chunk-bytes=32768");

        assertThat(configuration.getBaseDirPath()).isEqualTo("/data");
        assertThat(configuration.getRestrictToBaseDir()).isTrue();
        assertThat(configuration.getTitle()).isEqualTo("Files");
        assertThat(configuration.getDescription()).isEqualTo("demo");
        assertThat(configuration.getSymbolicLinkPolicy()).isEqualTo(SymbolicLinkPolicy.WITHIN_ROOT);
        assertThat(configuration.getGrantTtlSeconds()).isEqualTo(900);
        assertThat(configuration.getPasswordAttemptsPerMinute()).isEqualTo(3);
        assertThat(configuration.getPreviewMaxBytes()).isEqualTo(65536L);
        assertThat(configuration.getDownloadMaxConcurrent()).isEqualTo(4);
        assertThat(configuration.getLogsEnabled()).isTrue();
        assertThat(configuration.getLogsDefaultLines()).isEqualTo(100);
        assertThat(configuration.getLogsMaxLines()).isEqualTo(500);
        assertThat(configuration.getLogsMaxScanBytes()).isEqualTo(65536L);
        assertThat(configuration.getLogsPollIntervalMs()).isEqualTo(2000L);
        assertThat(configuration.getLogsMaxConnections()).isEqualTo(5);
        assertThat(configuration.getLogsMaxConnectionsPerSession()).isEqualTo(1);
        assertThat(configuration.getLogsMaxConnectionSeconds()).isEqualTo(600L);
        assertThat(configuration.getLogsMaxQueueBytes()).isEqualTo(131072);
        assertThat(configuration.getLogsMaxChunkBytes()).isEqualTo(32768);
    }

    @Test
    void folderPasswordsAreKeptAsConfigured() {
        Sfe4jConfiguration configuration = bind(
                "sfe4j.base-dir-path=/data",
                "sfe4j.folder-passwords.[/data/secret]=s3cret");

        assertThat(configuration.getFolderPasswords()).containsEntry("/data/secret", "s3cret");
    }

    @Test
    void outOfRangeValuesAreRejected() {
        assertThatThrownBy(() -> bind("sfe4j.access.grant-ttl-seconds=10"))
                .isInstanceOf(Sfe4jRuntimeException.class)
                .hasMessageContaining("sfe4j.access.grant-ttl-seconds");
        assertThatThrownBy(() -> bind("sfe4j.access.password-attempts-per-minute=0"))
                .isInstanceOf(Sfe4jRuntimeException.class)
                .hasMessageContaining("sfe4j.access.password-attempts-per-minute");
        assertThatThrownBy(() -> bind("sfe4j.preview.max-bytes=1"))
                .isInstanceOf(Sfe4jRuntimeException.class)
                .hasMessageContaining("sfe4j.preview.max-bytes");
        assertThatThrownBy(() -> bind("sfe4j.download.max-concurrent=1000"))
                .isInstanceOf(Sfe4jRuntimeException.class)
                .hasMessageContaining("sfe4j.download.max-concurrent");
        assertThatThrownBy(() -> bind("sfe4j.logs.default-lines=0"))
                .isInstanceOf(Sfe4jRuntimeException.class)
                .hasMessageContaining("sfe4j.logs.default-lines");
        assertThatThrownBy(() -> bind("sfe4j.logs.max-connections-per-session=64"))
                .isInstanceOf(Sfe4jRuntimeException.class)
                .hasMessageContaining("sfe4j.logs.max-connections-per-session");
    }

    @Test
    void logEndpointsAreOnlyRegisteredWhenTheFeatureIsEnabled(@TempDir Path baseDir) {
        starterRunner
                .withPropertyValues("sfe4j.base-dir-path=" + baseDir, "sfe4j.logs.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(
                            com.amoylabs.sfe4j.spring.boot.controller.TextApiController.class);
                    assertThat(context).doesNotHaveBean(
                            com.amoylabs.sfe4j.spring.boot.logs.LogFollowManager.class);
                });

        starterRunner
                .withPropertyValues("sfe4j.base-dir-path=" + baseDir, "sfe4j.logs.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(
                            com.amoylabs.sfe4j.spring.boot.controller.TextApiController.class);
                    assertThat(context).hasSingleBean(
                            com.amoylabs.sfe4j.spring.boot.logs.LogFollowManager.class);
                });
    }

    @Test
    void blankBaseDirFallsBackToRoot() {
        assertThat(bind("sfe4j.base-dir-path=   ").getBaseDirPath()).isEqualTo("/");
    }

    @Test
    void unknownSymbolicLinkPolicyIsRejected() {
        assertThatThrownBy(() -> SymbolicLinkPolicy.from("follow"))
                .isInstanceOf(Sfe4jRuntimeException.class)
                .hasMessageContaining("within-root");

        assertThatThrownBy(() -> bind("sfe4j.access.symbolic-link-policy=follow"))
                .isInstanceOf(Sfe4jRuntimeException.class)
                .hasMessageContaining("symbolic-link-policy");
    }

    @Test
    void theStarterItselfStopsWhenAValueIsOutOfRange(@TempDir Path baseDir) {
        assertThatThrownBy(() -> starterRunner
                .withPropertyValues("sfe4j.base-dir-path=" + baseDir, "sfe4j.preview.max-bytes=1")
                .run(context -> context.getBean(Sfe4jConfiguration.class)))
                .hasRootCauseMessage("sfe4j.preview.max-bytes must be between 1024 and 16777216 but was 1.");
    }

    @Test
    void unsetValuesDoNotBypassTheDefaultEvenWithoutTheAutoConfiguration() {
        Sfe4jConfiguration configuration = new Sfe4jProperties().toConfiguration();

        assertThat(configuration.getBaseDirPath()).isEqualTo("/");
        assertThat(configuration.getSymbolicLinkPolicy()).isEqualTo(SymbolicLinkPolicy.DENY);
        assertThat(configuration.getPreviewMaxBytes()).isEqualTo(1048576L);
    }

    private Sfe4jConfiguration bind(String... propertyValues) {
        AtomicReference<Sfe4jConfiguration> holder = new AtomicReference<>();
        propertiesRunner.withPropertyValues(propertyValues)
                .run(context -> holder.set(context.getBean(Sfe4jProperties.class).toConfiguration()));
        return holder.get();
    }
}
