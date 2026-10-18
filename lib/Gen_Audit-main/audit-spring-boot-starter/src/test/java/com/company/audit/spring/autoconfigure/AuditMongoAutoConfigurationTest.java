package com.company.audit.spring.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.audit.core.api.ChainedRecord;
import com.company.audit.core.api.PartitionContext;
import com.company.audit.core.api.exception.SeqConflictException;
import com.company.audit.core.port.ChainRepository;
import com.company.audit.spring.ingestion.AuditRecorder;
import com.company.audit.spring.partition.PartitionRegistry;
import com.company.audit.spring.port.EventStore;
import com.mongodb.client.MongoClients;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Proves the double {@code @ConditionalOnBean} on {@link AuditIngestionAutoConfiguration} —
 * {@code AuditRecorder} must appear only when both a ledger ({@code AuditAppender}) and a rich
 * event store ({@code EventStore}) are present, not merely when the auto-configuration class is
 * annotated that way.
 */
class AuditMongoAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(FakeChainRepositoryConfig.class, FakePartitionRegistryConfig.class)
            .withConfiguration(AutoConfigurations.of(
                    AuditCoreAutoConfiguration.class,
                    AuditMongoAutoConfiguration.class,
                    AuditIngestionAutoConfiguration.class));

    @Test
    void withOnlyJpaClassesPresentNoEventStoreOrRecorderBeanExists() {
        runner.withClassLoader(new FilteredClassLoader(MongoRepository.class)).run(context -> {
            assertThat(context).doesNotHaveBean(EventStore.class);
            assertThat(context).doesNotHaveBean(AuditRecorder.class);
        });
    }

    @Test
    void withJpaAndMongoPresentBothEventStoreAndRecorderBeansExist() {
        runner.withUserConfiguration(FakeMongoTemplateConfig.class).run(context -> {
            assertThat(context).hasSingleBean(EventStore.class);
            assertThat(context).hasSingleBean(AuditRecorder.class);
        });
    }

    @Configuration
    static class FakeChainRepositoryConfig {

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
    }

    @Configuration
    static class FakePartitionRegistryConfig {

        @Bean
        PartitionRegistry partitionRegistry() {
            return partitionKey -> new PartitionContext(partitionKey, Instant.EPOCH);
        }
    }

    @Configuration
    static class FakeMongoTemplateConfig {

        @Bean
        MongoTemplate mongoTemplate() {
            // A MongoClient does not connect until a query actually runs, so this is safe to
            // create against a bogus address purely to satisfy @EnableMongoRepositories' need
            // for a MongoOperations bean — no real connection is required for this test.
            return new MongoTemplate(MongoClients.create("mongodb://localhost:1"), "audit_mongo_autoconfig_test");
        }
    }
}
