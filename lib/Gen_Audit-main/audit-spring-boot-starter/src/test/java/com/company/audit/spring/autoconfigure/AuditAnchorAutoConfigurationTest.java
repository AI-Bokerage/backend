package com.company.audit.spring.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.audit.core.api.ChainedRecord;
import com.company.audit.core.api.exception.SeqConflictException;
import com.company.audit.core.port.ChainRepository;
import com.company.audit.core.port.ObjectLockPort;
import com.company.audit.spring.anchor.AnchorPublishFailureLoggingListener;
import com.company.audit.spring.anchor.AnchorPublisher;
import com.company.audit.spring.anchor.adapter.S3ObjectLockAdapter;
import com.company.audit.spring.partition.PartitionCatalog;
import com.company.audit.spring.scheduling.AnchorPublisherJob;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Proves the {@code @ConditionalOnClass(S3Client.class)},
 * {@code @ConditionalOnBean({ChainRepository.class, PartitionCatalog.class})}, and
 * {@code @ConditionalOnProperty(prefix = "audit.anchor", name = "bucket")} gates on
 * {@link AuditAnchorAutoConfiguration} — the last of which exists specifically so this
 * configuration stays inert in any application that has chain/partition beans but never
 * configured a bucket, rather than always eagerly constructing an {@link S3Client} (see that
 * class's Javadoc for the failing test that uncovered why).
 */
class AuditAnchorAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(FakeAnchorDepsConfig.class)
            .withConfiguration(AutoConfigurations.of(AuditMetricsAutoConfiguration.class, AuditAnchorAutoConfiguration.class))
            .withPropertyValues("audit.anchor.bucket=test-anchor-bucket");

    @Test
    void withS3ClientClassExcludedNoBeansAreCreated() {
        new ApplicationContextRunner()
                .withUserConfiguration(FakeAnchorDepsConfig.class)
                .withConfiguration(AutoConfigurations.of(AuditMetricsAutoConfiguration.class, AuditAnchorAutoConfiguration.class))
                .withPropertyValues("audit.anchor.bucket=test-anchor-bucket")
                .withClassLoader(new FilteredClassLoader(S3Client.class))
                .run(context -> assertThat(context).doesNotHaveBean(AnchorPublisherJob.class));
    }

    @Test
    void withoutChainRepositoryOrPartitionCatalogNoBeansAreCreated() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(AuditMetricsAutoConfiguration.class, AuditAnchorAutoConfiguration.class))
                .withPropertyValues("audit.anchor.bucket=test-anchor-bucket")
                .run(context -> assertThat(context).doesNotHaveBean(AnchorPublisherJob.class));
    }

    @Test
    void withoutBucketPropertyNoBeansAreCreated() {
        new ApplicationContextRunner()
                .withUserConfiguration(FakeAnchorDepsConfig.class)
                .withConfiguration(AutoConfigurations.of(AuditMetricsAutoConfiguration.class, AuditAnchorAutoConfiguration.class))
                .run(context -> assertThat(context).doesNotHaveBean(AnchorPublisherJob.class));
    }

    @Test
    void withDependenciesAndBucketPropertyPresentAllBeansAreCreated() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(AnchorPublisherJob.class);
            assertThat(context).hasSingleBean(AnchorPublisher.class);
            assertThat(context).hasSingleBean(ObjectLockPort.class);
            assertThat(context).hasSingleBean(AnchorPublishFailureLoggingListener.class);
        });
    }

    @Test
    void userSuppliedObjectLockPortSuppressesTheDefaultS3Adapter() {
        ObjectLockPort userPort = (partitionKey, tipHash, publishedAt) -> "user-supplied-ref";
        runner.withBean("userObjectLockPort", ObjectLockPort.class, () -> userPort).run(context -> {
            assertThat(context).hasSingleBean(ObjectLockPort.class);
            assertThat(context).doesNotHaveBean(S3ObjectLockAdapter.class);
            assertThat(context.getBean(ObjectLockPort.class)).isSameAs(userPort);
        });
    }

    @Test
    void userSuppliedAnchorPublishFailureLoggingListenerSuppressesTheDefault() {
        AnchorPublishFailureLoggingListener userListener = new AnchorPublishFailureLoggingListener();
        runner.withBean(
                        "userAnchorPublishFailureLoggingListener",
                        AnchorPublishFailureLoggingListener.class,
                        () -> userListener)
                .run(context -> {
                    assertThat(context).hasSingleBean(AnchorPublishFailureLoggingListener.class);
                    assertThat(context.getBean(AnchorPublishFailureLoggingListener.class)).isSameAs(userListener);
                });
    }

    @Configuration
    static class FakeAnchorDepsConfig {

        @Bean
        ChainRepository chainRepository() {
            return new ChainRepository() {
                @Override
                public Optional<ChainedRecord> findTip(String partitionKey) {
                    return Optional.empty();
                }

                @Override
                public void append(ChainedRecord record) throws SeqConflictException {
                    // not exercised by this test
                }

                @Override
                public List<ChainedRecord> findAllOrderedBySeq(String partitionKey) {
                    return List.of();
                }
            };
        }

        @Bean
        PartitionCatalog partitionCatalog() {
            return List::of;
        }

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.EPOCH, java.time.ZoneOffset.UTC);
        }

        // Supplied here (rather than left to AuditAnchorAutoConfiguration's own default bean) so
        // that merely proving the auto-configuration wires beans together doesn't also require a
        // reachable AWS region/credentials chain — S3Client.create() resolves a region eagerly at
        // construction time and would otherwise fail these tests for a reason unrelated to what
        // they're proving.
        @Bean
        S3Client s3Client() {
            return S3Client.builder()
                    .region(Region.US_EAST_1)
                    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                    .build();
        }
    }
}
