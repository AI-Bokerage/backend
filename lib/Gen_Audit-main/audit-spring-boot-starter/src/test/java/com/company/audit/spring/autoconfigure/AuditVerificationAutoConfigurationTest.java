package com.company.audit.spring.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.audit.core.api.AuditVerifier;
import com.company.audit.core.api.ChainedRecord;
import com.company.audit.core.api.PartitionContext;
import com.company.audit.core.api.exception.SeqConflictException;
import com.company.audit.core.port.ChainRepository;
import com.company.audit.spring.partition.PartitionCatalog;
import com.company.audit.spring.partition.PartitionRegistry;
import com.company.audit.spring.verification.ChainBreakLoggingListener;
import com.company.audit.spring.verification.ChainVerifierJob;
import jakarta.persistence.EntityManager;
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

/**
 * Proves the {@code @ConditionalOnBean({AuditVerifier.class, PartitionRegistry.class})} gate on
 * {@link AuditVerificationAutoConfiguration}, and that a user-supplied
 * {@link ChainBreakLoggingListener} suppresses the default one.
 */
class AuditVerificationAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(FakeVerificationDepsConfig.class)
            .withConfiguration(AutoConfigurations.of(
                    AuditMetricsAutoConfiguration.class, AuditVerificationAutoConfiguration.class));

    @Test
    void withJpaExcludedNoChainVerifierJobBeanIsCreated() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        AuditMetricsAutoConfiguration.class, AuditVerificationAutoConfiguration.class))
                .withClassLoader(new FilteredClassLoader(EntityManager.class))
                .run(context -> assertThat(context).doesNotHaveBean(ChainVerifierJob.class));
    }

    @Test
    void withDependenciesPresentChainVerifierJobBeanIsCreated() {
        runner.run(context -> assertThat(context).hasSingleBean(ChainVerifierJob.class));
    }

    @Test
    void userSuppliedChainBreakLoggingListenerSuppressesTheDefault() {
        ChainBreakLoggingListener userListener = new ChainBreakLoggingListener();
        runner.withUserConfiguration(FakeVerificationDepsConfig.class)
                .withBean("userChainBreakLoggingListener", ChainBreakLoggingListener.class, () -> userListener)
                .run(context -> {
                    assertThat(context).hasSingleBean(ChainBreakLoggingListener.class);
                    assertThat(context.getBean(ChainBreakLoggingListener.class)).isSameAs(userListener);
                });
    }

    @Configuration
    static class FakeVerificationDepsConfig {

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
        AuditVerifier auditVerifier(ChainRepository chainRepository) {
            return AuditVerifier.create(chainRepository);
        }

        @Bean
        PartitionRegistry partitionRegistry() {
            return partitionKey -> new PartitionContext(partitionKey, Instant.EPOCH);
        }

        @Bean
        PartitionCatalog partitionCatalog() {
            return List::of;
        }

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }
}
