package com.company.audit.spring;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.audit.core.api.AuditAppender;
import com.company.audit.core.api.AuditEvent;
import com.company.audit.core.api.AuditVerifier;
import com.company.audit.core.api.ChainedRecord;
import com.company.audit.core.api.PartitionContext;
import com.company.audit.core.api.VerificationResult;
import com.company.audit.core.api.enums.ActorType;
import com.company.audit.core.api.enums.AuditCategory;
import com.company.audit.core.api.enums.AuditChainStatus;
import com.company.audit.spring.partition.PartitionRegistry;
import com.company.audit.spring.support.AbstractPostgresIntegrationTest;
import com.company.audit.spring.support.TestApplication;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Boots a real Spring context with both {@code audit-spring-boot-starter} auto-configurations
 * against a genuine Testcontainers Postgres instance, then proves tamper detection using a raw
 * JDBC connection that bypasses the JPA layer entirely — simulating genuine insider DB access.
 */
@SpringBootTest(classes = TestApplication.class)
class AuditStarterEndToEndTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private AuditAppender auditAppender;

    @Autowired
    private AuditVerifier auditVerifier;

    @Autowired
    private PartitionRegistry partitionRegistry;

    @Autowired
    private Clock clock;

    @Test
    void appendedEventsVerifyOkThenTamperingViaRawJdbcIsDetected() throws Exception {
        String partitionKey = "e2e-partition-" + UUID.randomUUID();
        PartitionContext partitionContext = partitionRegistry.resolve(partitionKey);

        List<ChainedRecord> records = new ArrayList<>();
        for (int i = 0; i < 55; i++) {
            AuditEvent event = AuditEvent.builder()
                    .id(UUID.randomUUID().toString())
                    .partitionKey(partitionKey)
                    .eventType("E2E_EVENT")
                    .actorType(ActorType.SERVICE)
                    .actorId("actor-1")
                    .category(AuditCategory.DATA_MUTATION)
                    .occurredAt(clock.instant())
                    .payload(Map.of("index", i))
                    .build();
            records.add(auditAppender.append(event, partitionContext));
        }

        VerificationResult okResult = auditVerifier.verify(partitionKey, partitionContext);
        assertThat(okResult.status()).isEqualTo(AuditChainStatus.OK);

        ChainedRecord tampered = records.get(24);
        UUID auditId = UUID.fromString(tampered.event().id());
        byte[] bogusHash = MessageDigest.getInstance("SHA-256").digest("tampered-via-raw-jdbc".getBytes());

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                PreparedStatement statement =
                        connection.prepareStatement("UPDATE audit_immutable SET event_hash = ? WHERE audit_id = ?")) {
            statement.setBytes(1, bogusHash);
            statement.setObject(2, auditId);
            int updated = statement.executeUpdate();
            assertThat(updated).isEqualTo(1);
        }

        VerificationResult tamperedResult = auditVerifier.verify(partitionKey, partitionContext);
        assertThat(tamperedResult.status()).isEqualTo(AuditChainStatus.HASH_MISMATCH);
        assertThat(tamperedResult.breakAtSeq()).contains(tampered.seq());
    }
}
