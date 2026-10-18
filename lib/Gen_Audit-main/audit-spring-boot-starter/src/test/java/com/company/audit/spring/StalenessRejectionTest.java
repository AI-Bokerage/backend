package com.company.audit.spring;

import static org.assertj.core.api.Assertions.assertThat;

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
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Deliberately constructs the exact race {@link JpaChainRepository}'s in-lock freshness check
 * exists to catch: a raw JDBC connection holds the partition's advisory lock open (with an
 * uncommitted insert behind it) while a second, already-in-flight {@code append()} call —
 * computed from a tip read <em>before</em> the first transaction commits — attempts to proceed.
 *
 * <p>The point of this test, specifically: proving the second call is rejected <em>before</em>
 * any insert is attempted, not merely that it eventually fails via the {@code UNIQUE} constraint.
 * {@link org.springframework.boot.test.context.SpringBootTest} + a real Postgres, not mocks —
 * the blocking behavior itself (confirmed via {@code Future.isDone()}) is what proves the second
 * caller was actually waiting on the lock, not racing ahead of it.
 */
@SpringBootTest(classes = TestApplication.class)
class StalenessRejectionTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private JpaChainRepository repository;

    @Autowired
    private PartitionRegistry partitionRegistry;

    @Test
    void secondCallerRejectedBeforeInsertWhenTipChangedWhileWaitingForLock() throws Exception {
        String partitionKey = "staleness-test-" + UUID.randomUUID();
        partitionRegistry.resolve(partitionKey);

        HashValue genesisHash = genesisHash();
        ChainedRecord winner = buildRecord(partitionKey, 1, genesisHash, "winner");
        // Computed as if the partition were still empty — true at the moment a real caller would
        // have read it, stale by the time this attempt actually reaches the lock.
        ChainedRecord staleCandidate = buildRecord(partitionKey, 1, genesisHash, "stale-caller");

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (Connection rawConnection =
                DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            rawConnection.setAutoCommit(false);
            try (PreparedStatement lockStatement =
                    rawConnection.prepareStatement("SELECT pg_advisory_xact_lock(hashtext(?))")) {
                lockStatement.setString(1, partitionKey);
                lockStatement.execute();
            }
            insertRaw(rawConnection, winner);
            // Still uncommitted: the advisory lock is held and winner's row is invisible to
            // every other transaction, including the one about to attempt staleCandidate.

            Future<Exception> rejectedAttempt = executor.submit(() -> {
                try {
                    repository.append(staleCandidate);
                    return null;
                } catch (Exception e) {
                    return e;
                }
            });

            Thread.sleep(500);
            assertThat(rejectedAttempt.isDone())
                    .as("the second append() call must still be blocked waiting for the lock we hold")
                    .isFalse();

            rawConnection.commit(); // releases the lock and makes winner's row visible

            Exception thrown = rejectedAttempt.get(10, TimeUnit.SECONDS);
            assertThat(thrown).isInstanceOf(SeqConflictException.class);
        } finally {
            executor.shutdown();
        }

        List<ChainedRecord> stored = repository.findAllOrderedBySeq(partitionKey);
        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).eventHash()).isEqualTo(winner.eventHash());
    }

    private void insertRaw(Connection connection, ChainedRecord record) throws Exception {
        String sql = "INSERT INTO audit_immutable "
                + "(audit_id, partition_key, seq, event_type, actor_type, actor_id, category, occurred_at, "
                + "payload_hash, prev_event_hash, event_hash, recorded_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, UUID.fromString(record.event().id()));
            statement.setString(2, record.event().partitionKey());
            statement.setLong(3, record.seq());
            statement.setString(4, record.event().eventType());
            statement.setString(5, record.event().actorType().name());
            statement.setString(6, record.event().actorId());
            statement.setString(7, record.event().category().name());
            statement.setObject(8, OffsetDateTime.ofInstant(record.event().occurredAt(), ZoneOffset.UTC));
            statement.setBytes(9, record.payloadHash().bytes());
            statement.setBytes(10, record.prevEventHash().bytes());
            statement.setBytes(11, record.eventHash().bytes());
            statement.setObject(12, OffsetDateTime.ofInstant(record.recordedAt(), ZoneOffset.UTC));
            statement.executeUpdate();
        }
    }

    private ChainedRecord buildRecord(String partitionKey, long seq, HashValue prevHash, String label)
            throws Exception {
        AuditEvent event = AuditEvent.builder()
                .id(UUID.randomUUID().toString())
                .partitionKey(partitionKey)
                .eventType("STALENESS_TEST_EVENT")
                .actorType(ActorType.SERVICE)
                .actorId("actor-1")
                .category(AuditCategory.DATA_MUTATION)
                .occurredAt(Instant.parse("2024-06-01T12:00:00Z"))
                .payload(Map.of("label", label))
                .build();
        HashValue payloadHash = HashValue.of(sha256("payload-" + label));
        HashValue eventHash = HashValue.of(sha256("event-" + label));
        return new ChainedRecord(event, seq, prevHash, payloadHash, eventHash, Instant.parse("2024-06-01T12:00:00Z"));
    }

    private HashValue genesisHash() throws Exception {
        return HashValue.of(sha256("genesis"));
    }

    private byte[] sha256(String input) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(input.getBytes());
    }
}
