package com.company.audit.spring;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.audit.core.api.PartitionContext;
import com.company.audit.spring.persistence.jpa.adapter.JpaPartitionRegistry;
import com.company.audit.spring.support.AbstractPostgresIntegrationTest;
import com.company.audit.spring.support.TestApplication;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@SpringBootTest(classes = TestApplication.class)
@Import(JpaPartitionRegistryTest.FixedClockConfig.class)
class JpaPartitionRegistryTest extends AbstractPostgresIntegrationTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2024-01-01T00:00:00Z");

    @Autowired
    private JpaPartitionRegistry registry;

    @Test
    void firstCallPersistsAndReturnsTheClockInstant() {
        String partitionKey = "partition-registry-test-" + UUID.randomUUID();

        PartitionContext result = registry.resolve(partitionKey);

        assertThat(result.partitionKey()).isEqualTo(partitionKey);
        assertThat(result.createdAt()).isEqualTo(FIXED_INSTANT);
    }

    @Test
    void repeatedCallsForTheSamePartitionReturnTheSamePersistedValue() {
        String partitionKey = "partition-registry-test-" + UUID.randomUUID();

        PartitionContext first = registry.resolve(partitionKey);
        PartitionContext second = registry.resolve(partitionKey);
        PartitionContext third = registry.resolve(partitionKey);

        assertThat(second.createdAt()).isEqualTo(first.createdAt());
        assertThat(third.createdAt()).isEqualTo(first.createdAt());
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        Clock clock() {
            return Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
        }
    }
}
