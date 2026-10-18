package com.company.audit.spring.partition;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.audit.core.api.PartitionContext;
import com.company.audit.spring.support.AbstractPostgresIntegrationTest;
import com.company.audit.spring.support.TestApplication;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@SpringBootTest(classes = TestApplication.class)
@Import(JpaPartitionCatalogTest.FixedClockConfig.class)
class JpaPartitionCatalogTest extends AbstractPostgresIntegrationTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2024-03-01T00:00:00Z");

    @Autowired
    private PartitionRegistry partitionRegistry;

    @Autowired
    private PartitionCatalog partitionCatalog;

    @Test
    void listAllReturnsExactlyThePersistedPartitionsWithCorrectCreatedAt() {
        String runId = UUID.randomUUID().toString();
        List<String> keys = List.of(
                "catalog-test-" + runId + "-a", "catalog-test-" + runId + "-b", "catalog-test-" + runId + "-c");
        keys.forEach(partitionRegistry::resolve);

        List<PartitionContext> matching = partitionCatalog.listAll().stream()
                .filter(ctx -> ctx.partitionKey().startsWith("catalog-test-" + runId))
                .toList();

        assertThat(matching).hasSize(keys.size());
        assertThat(matching.stream().map(PartitionContext::partitionKey).collect(java.util.stream.Collectors.toSet()))
                .isEqualTo(Set.copyOf(keys));
        assertThat(matching).allSatisfy(ctx -> assertThat(ctx.createdAt()).isEqualTo(FIXED_INSTANT));
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        Clock clock() {
            return Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
        }
    }
}
