package com.company.audit.spring.verification;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.audit.core.api.AuditEvent;
import com.company.audit.core.api.VerificationResult;
import com.company.audit.core.api.enums.ActorType;
import com.company.audit.core.api.enums.AuditCategory;
import com.company.audit.core.api.enums.AuditChainStatus;
import com.company.audit.spring.ingestion.AuditRecorder;
import com.company.audit.spring.support.AbstractPostgresAndMongoIntegrationTest;
import com.company.audit.spring.support.TestApplication;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;

@SpringBootTest(classes = TestApplication.class)
@Import(ChainVerifierJobIntegrationTest.CapturingListenerConfig.class)
class ChainVerifierJobIntegrationTest extends AbstractPostgresAndMongoIntegrationTest {

    @Autowired
    private AuditRecorder auditRecorder;

    @Autowired
    private ChainVerifierJob chainVerifierJob;

    @Autowired
    private Clock clock;

    @Autowired
    private CapturingListener capturingListener;

    @Test
    void runNowReportsCleanAndBrokenPartitionsAndPublishesExactlyOneBreakEvent() throws Exception {
        String runId = UUID.randomUUID().toString();
        String cleanA = "verifier-job-test-" + runId + "-clean-a";
        String cleanB = "verifier-job-test-" + runId + "-clean-b";
        String broken = "verifier-job-test-" + runId + "-broken";

        recordEvents(cleanA, 3);
        recordEvents(cleanB, 4);
        List<com.company.audit.core.api.ChainedRecord> brokenRecords = recordEvents(broken, 5);

        UUID auditId = UUID.fromString(brokenRecords.get(2).event().id());
        byte[] bogusHash = MessageDigest.getInstance("SHA-256").digest("tampered-via-raw-jdbc".getBytes());
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                PreparedStatement statement =
                        connection.prepareStatement("UPDATE audit_immutable SET event_hash = ? WHERE audit_id = ?")) {
            statement.setBytes(1, bogusHash);
            statement.setObject(2, auditId);
            statement.executeUpdate();
        }

        capturingListener.events.clear();
        VerificationSummary summary = chainVerifierJob.runNow();

        assertThat(summary.resultsByPartition().get(cleanA).status()).isEqualTo(AuditChainStatus.OK);
        assertThat(summary.resultsByPartition().get(cleanB).status()).isEqualTo(AuditChainStatus.OK);
        VerificationResult brokenResult = summary.resultsByPartition().get(broken);
        assertThat(brokenResult.status()).isEqualTo(AuditChainStatus.HASH_MISMATCH);
        assertThat(brokenResult.breakAtSeq()).contains(brokenRecords.get(2).seq());

        assertThat(summary.startedAt()).isNotNull();
        assertThat(summary.finishedAt()).isNotNull();
        assertThat(summary.finishedAt()).isAfterOrEqualTo(summary.startedAt());

        List<AuditChainBreakDetectedEvent> capturedForThisRun = capturingListener.events.stream()
                .filter(event -> event.partitionKey().equals(broken))
                .toList();
        assertThat(capturedForThisRun).hasSize(1);

        boolean anyEventForCleanPartitions = capturingListener.events.stream()
                .anyMatch(event -> event.partitionKey().equals(cleanA) || event.partitionKey().equals(cleanB));
        assertThat(anyEventForCleanPartitions).isFalse();
    }

    private List<com.company.audit.core.api.ChainedRecord> recordEvents(String partitionKey, int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> auditRecorder.record(buildEvent(partitionKey, i)))
                .toList();
    }

    private AuditEvent buildEvent(String partitionKey, int index) {
        return AuditEvent.builder()
                .id(UUID.randomUUID().toString())
                .partitionKey(partitionKey)
                .eventType("VERIFIER_JOB_TEST_EVENT")
                .actorType(ActorType.SERVICE)
                .actorId("actor-1")
                .category(AuditCategory.DATA_MUTATION)
                .occurredAt(clock.instant())
                .payload(Map.of("index", index))
                .build();
    }

    @TestConfiguration
    static class CapturingListenerConfig {

        @Bean
        CapturingListener capturingListener() {
            return new CapturingListener();
        }
    }

    static class CapturingListener {

        private final List<AuditChainBreakDetectedEvent> events = new CopyOnWriteArrayList<>();

        @EventListener
        void onChainBreak(AuditChainBreakDetectedEvent event) {
            events.add(event);
        }
    }
}
