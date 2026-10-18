package com.company.audit.spring.autoconfigure;

import com.company.audit.core.api.AuditVerifier;
import com.company.audit.spring.metrics.AuditMetricsRecorder;
import com.company.audit.spring.partition.JpaPartitionCatalog;
import com.company.audit.spring.partition.PartitionCatalog;
import com.company.audit.spring.partition.PartitionRegistry;
import com.company.audit.spring.persistence.jpa.repository.SpringDataChainPartitionRepository;
import com.company.audit.spring.verification.ChainBreakLoggingListener;
import com.company.audit.spring.verification.ChainVerifierJob;
import java.time.Clock;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Auto-configuration for scheduled chain verification.
 *
 * <p>Gated on both {@link AuditVerifier} and {@link PartitionRegistry} beans being present: the
 * former is what actually verifies a chain, and the latter's presence is this starter's existing
 * signal that the JPA ledger adapter is active at all (this configuration itself only ever reads
 * via {@link PartitionCatalog}, never through {@link PartitionRegistry} — see
 * {@link ChainVerifierJob}'s Javadoc for why).
 *
 * <p>Declares {@code @EnableScheduling} directly on itself rather than requiring a dedicated
 * {@code TaskScheduler} bean elsewhere for one cron job — Spring tolerates
 * {@code @EnableScheduling} being declared more than once across an application context, so this
 * is safe even if a consuming application also declares it.
 *
 * <p>Declares {@code after = AuditCoreAutoConfiguration.class} explicitly, even though it had
 * worked without one: {@link AuditCoreAutoConfiguration} is itself already ordered after
 * {@link AuditJpaAutoConfiguration}, so depending on it transitively orders this configuration
 * after both {@link AuditVerifier}'s and {@link PartitionRegistry}'s providers. Without this hint,
 * this class's relative order was only ever correct by an alphabetical-sort coincidence
 * ("AuditJpaAutoConfiguration" &lt; "AuditVerificationAutoConfiguration"), which broke the moment
 * {@code AuditAnchorAutoConfiguration} declared {@code after = AuditVerificationAutoConfiguration}
 * — Spring Boot's {@code AutoConfigurationSorter} does a depth-first walk of {@code after}
 * dependencies starting from "AuditAnchorAutoConfiguration" (alphabetically first), which pulled
 * this class to the very front of the sorted order, ahead of the Jpa/Core configurations whose
 * beans its class-level {@code @ConditionalOnBean} depends on — found via a failing test
 * ({@code ChainVerifierJobIntegrationTest} losing its {@code ChainVerifierJob} bean), not assumed.
 */
@AutoConfiguration(after = AuditCoreAutoConfiguration.class)
@EnableScheduling
@ConditionalOnBean({AuditVerifier.class, PartitionRegistry.class})
public class AuditVerificationAutoConfiguration {

    /**
     * Creates a new auto-configuration instance.
     */
    public AuditVerificationAutoConfiguration() {
    }

    /**
     * Provides the JPA-backed {@link PartitionCatalog}, unless a consuming application has
     * already supplied its own.
     *
     * @param repository the underlying Spring Data repository
     * @return a new catalog adapter
     */
    @Bean
    @ConditionalOnMissingBean(PartitionCatalog.class)
    public JpaPartitionCatalog partitionCatalog(SpringDataChainPartitionRepository repository) {
        return new JpaPartitionCatalog(repository);
    }

    /**
     * Provides the scheduled verification job.
     *
     * @param partitionCatalog enumerates every known partition
     * @param auditVerifier verifies a single partition's chain
     * @param eventPublisher publishes chain-break events
     * @param metricsRecorder records verification outcomes and timing
     * @param clock the clock used to timestamp runs and detected breaks
     * @return a new job
     */
    @Bean
    @ConditionalOnMissingBean(ChainVerifierJob.class)
    public ChainVerifierJob chainVerifierJob(
            PartitionCatalog partitionCatalog,
            AuditVerifier auditVerifier,
            ApplicationEventPublisher eventPublisher,
            AuditMetricsRecorder metricsRecorder,
            Clock clock) {
        return new ChainVerifierJob(partitionCatalog, auditVerifier, eventPublisher, metricsRecorder, clock);
    }

    /**
     * Provides the default chain-break logging listener, unless a consuming application has
     * already supplied its own bean of this type — replacing the default behavior wholesale,
     * rather than only ever adding a second listener alongside it.
     *
     * @return a new listener
     */
    @Bean
    @ConditionalOnMissingBean(ChainBreakLoggingListener.class)
    public ChainBreakLoggingListener chainBreakLoggingListener() {
        return new ChainBreakLoggingListener();
    }
}
