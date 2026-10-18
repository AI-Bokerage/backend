package com.company.audit.spring.autoconfigure;

import com.company.audit.core.port.ChainRepository;
import com.company.audit.core.port.ObjectLockPort;
import com.company.audit.spring.anchor.AnchorPublishFailureLoggingListener;
import com.company.audit.spring.anchor.AnchorPublisher;
import com.company.audit.spring.anchor.adapter.S3ObjectLockAdapter;
import com.company.audit.spring.config.AuditProperties;
import com.company.audit.spring.metrics.AuditMetricsRecorder;
import com.company.audit.spring.partition.PartitionCatalog;
import com.company.audit.spring.scheduling.AnchorPublisherJob;
import java.time.Clock;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Auto-configuration for scheduled anchor publishing.
 *
 * <p>Gated on both {@link ChainRepository} and {@link PartitionCatalog} beans being present: the
 * former is what {@link AnchorPublisher} reads a partition's tip from, and the latter is what
 * {@link AnchorPublisherJob} enumerates partitions with — the same catalog introduced for
 * scheduled chain verification, not a new enumeration mechanism.
 *
 * <p>Also gated on {@code audit.anchor.bucket} being explicitly set: {@link AuditProperties}
 * deliberately gives that property no default (a wrong default silently anchoring to the wrong
 * bucket is worse than not anchoring at all), so this configuration only activates once a
 * consuming application opts in by naming a bucket — rather than always activating and eagerly
 * constructing an {@link S3Client}, which fails fast with an unrelated
 * "unable to load region" error in any application or test that merely happens to have
 * {@link ChainRepository} and {@link PartitionCatalog} beans but never intended to use anchoring
 * at all (found via a failing pre-existing test, not assumed).
 *
 * <p>Declares {@code @EnableScheduling} directly on itself, same as
 * {@code AuditVerificationAutoConfiguration} — Spring tolerates it being declared more than once
 * across an application context.
 *
 * <p>Declares {@code after = AuditVerificationAutoConfiguration.class} explicitly: without an
 * ordering hint, Spring Boot's deferred {@code @ConditionalOnBean} evaluation has no reason to
 * process this class after the one that actually provides {@link PartitionCatalog}, and can
 * evaluate this class's condition first, finding no {@link PartitionCatalog} bean yet and
 * silently skipping this configuration — found via an actual failing test
 * ({@code ChainVerifierJobIntegrationTest} losing its {@code ChainVerifierJob} bean the moment
 * this class was added to the auto-configuration chain, with no other change), not assumed.
 */
@AutoConfiguration(after = AuditVerificationAutoConfiguration.class)
@EnableScheduling
@ConditionalOnClass(S3Client.class)
@ConditionalOnBean({ChainRepository.class, PartitionCatalog.class})
@ConditionalOnProperty(prefix = "audit.anchor", name = "bucket")
@EnableConfigurationProperties(AuditProperties.class)
public class AuditAnchorAutoConfiguration {

    /**
     * Creates a new auto-configuration instance.
     */
    public AuditAnchorAutoConfiguration() {
    }

    /**
     * Provides a default {@link S3Client}, unless a consuming application has already supplied
     * its own (for example, one configured with non-default credentials, region, or — as
     * {@code audit-demo} does for its LocalStack instance — an endpoint override).
     *
     * @return a new client using the AWS SDK's default region/credentials resolution
     */
    @Bean
    @ConditionalOnMissingBean(S3Client.class)
    public S3Client s3Client() {
        return S3Client.create();
    }

    /**
     * Provides the S3-backed {@link ObjectLockPort}, unless a consuming application has already
     * supplied its own.
     *
     * @param s3Client the S3 client to publish anchors through
     * @param properties the starter's configuration properties
     * @return a new adapter
     */
    @Bean
    @ConditionalOnMissingBean(ObjectLockPort.class)
    public S3ObjectLockAdapter objectLockPort(S3Client s3Client, AuditProperties properties) {
        AuditProperties.Anchor anchor = properties.getAnchor();
        return new S3ObjectLockAdapter(s3Client, anchor.getBucket(), anchor.getKeyPrefix(), anchor.getRetentionYears());
    }

    /**
     * Provides the single-partition anchor publisher.
     *
     * @param chainRepository reads a partition's current chain tip
     * @param objectLockPort publishes anchors to immutable object storage
     * @param clock the clock used to timestamp published anchors
     * @return a new publisher
     */
    @Bean
    @ConditionalOnMissingBean(AnchorPublisher.class)
    public AnchorPublisher anchorPublisher(ChainRepository chainRepository, ObjectLockPort objectLockPort, Clock clock) {
        return new AnchorPublisher(chainRepository, objectLockPort, clock);
    }

    /**
     * Provides the scheduled anchor-publishing job.
     *
     * @param partitionCatalog enumerates every known partition
     * @param anchorPublisher publishes a single partition's anchor
     * @param eventPublisher publishes anchor-publish-failure events
     * @param metricsRecorder records anchoring outcomes and timing
     * @param clock the clock used to timestamp runs and detected failures
     * @return a new job
     */
    @Bean
    @ConditionalOnMissingBean(AnchorPublisherJob.class)
    public AnchorPublisherJob anchorPublisherJob(
            PartitionCatalog partitionCatalog,
            AnchorPublisher anchorPublisher,
            ApplicationEventPublisher eventPublisher,
            AuditMetricsRecorder metricsRecorder,
            Clock clock) {
        return new AnchorPublisherJob(partitionCatalog, anchorPublisher, eventPublisher, metricsRecorder, clock);
    }

    /**
     * Provides the default anchor-publish-failure logging listener, unless a consuming
     * application has already supplied its own bean of this type.
     *
     * @return a new listener
     */
    @Bean
    @ConditionalOnMissingBean(AnchorPublishFailureLoggingListener.class)
    public AnchorPublishFailureLoggingListener anchorPublishFailureLoggingListener() {
        return new AnchorPublishFailureLoggingListener();
    }
}
