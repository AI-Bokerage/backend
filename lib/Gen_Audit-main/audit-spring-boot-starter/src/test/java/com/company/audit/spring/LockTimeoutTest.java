package com.company.audit.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.audit.core.api.AuditEvent;
import com.company.audit.core.api.ChainedRecord;
import com.company.audit.core.api.HashValue;
import com.company.audit.core.api.enums.ActorType;
import com.company.audit.core.api.enums.AuditCategory;
import com.company.audit.core.api.exception.SeqConflictException;
import com.company.audit.spring.partition.PartitionRegistry;
import com.company.audit.spring.persistence.jpa.adapter.JpaChainRepository;
import com.company.audit.spring.support.AbstractPostgresIntegrationTest;
import com.company.audit.spring.support.TestApplication;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Proves {@code audit.jpa.partition-lock-timeout} actually bounds how long {@code append()}
 * waits to acquire a partition's advisory lock: holds the lock open via a raw JDBC connection far
 * longer than the configured window, and confirms a concurrent {@code append()} attempt fails
 * with {@link SeqConflictException} within roughly that window — not immediately (which would
 * mean the timeout isn't actually being waited out), and not indefinitely (which would mean it
 * isn't bounding anything at all).
 *
 * <p>Configures a short 1-second timeout via {@code properties}, giving this test class its own
 * dedicated Spring context distinct from every other test's default-5-second one.
 */
@SpringBootTest(classes = TestApplication.class, properties = "audit.jpa.partition-lock-timeout=1s")
class LockTimeoutTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private JpaChainRepository repository;

    @Autowired
    private PartitionRegistry partitionRegistry;

    @Test
    void concurrentAppendFailsWithSeqConflictWithinRoughlyTheConfiguredTimeout() throws Exception {
        String partitionKey = "lock-timeout-test-" + UUID.randomUUID();
        partitionRegistry.resolve(partitionKey);

        ChainedRecord candidate = buildRecord(partitionKey, 1, genesisHash());

        try (Connection rawConnection =
                DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            rawConnection.setAutoCommit(false);
            try (PreparedStatement lockStatement =
                    rawConnection.prepareStatement("SELECT pg_advisory_xact_lock(hashtext(?))")) {
                lockStatement.setString(1, partitionKey);
                lockStatement.execute();
            }
            // Deliberately never committed for the rest of this test: the lock stays held far
            // longer than the configured 1-second timeout. Closing the connection without a
            // commit rolls it back, releasing the lock automatically once this block exits.

            Instant startedAt = Instant.now();
            assertThatThrownBy(() -> repository.append(candidate)).isInstanceOf(SeqConflictException.class);
            Duration elapsed = Duration.between(startedAt, Instant.now());

            assertThat(elapsed)
                    .as("must actually wait roughly the configured timeout, not fail immediately")
                    .isGreaterThanOrEqualTo(Duration.ofMillis(800));
            assertThat(elapsed)
                    .as("must be bounded, not hang indefinitely")
                    .isLessThan(Duration.ofSeconds(15));
        }
    }

    private ChainedRecord buildRecord(String partitionKey, long seq, HashValue prevHash) throws Exception {
        AuditEvent event = AuditEvent.builder()
                .id(UUID.randomUUID().toString())
                .partitionKey(partitionKey)
                .eventType("LOCK_TIMEOUT_TEST_EVENT")
                .actorType(ActorType.SERVICE)
                .actorId("actor-1")
                .category(AuditCategory.DATA_MUTATION)
                .occurredAt(Instant.parse("2024-06-01T12:00:00Z"))
                .payload(Map.of())
                .build();
        HashValue payloadHash = HashValue.of(sha256("payload"));
        HashValue eventHash = HashValue.of(sha256("event"));
        return new ChainedRecord(event, seq, prevHash, payloadHash, eventHash, Instant.parse("2024-06-01T12:00:00Z"));
    }

    private HashValue genesisHash() throws Exception {
        return HashValue.of(sha256("genesis"));
    }

    private byte[] sha256(String input) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(input.getBytes());
    }
}
