package com.company.audit.spring.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.audit.core.api.AuditEvent;
import com.company.audit.core.api.AuditVerifier;
import com.company.audit.core.api.ChainedRecord;
import com.company.audit.core.api.PartitionContext;
import com.company.audit.core.api.VerificationResult;
import com.company.audit.core.api.enums.ActorType;
import com.company.audit.core.api.enums.AuditCategory;
import com.company.audit.core.api.enums.AuditChainStatus;
import com.company.audit.spring.partition.PartitionRegistry;
import com.company.audit.spring.persistence.mongo.document.AuditEventDocument;
import com.company.audit.spring.persistence.mongo.repository.SpringDataAuditEventMongoRepository;
import com.company.audit.spring.port.EventStore;
import com.company.audit.spring.support.AbstractPostgresAndMongoIntegrationTest;
import com.company.audit.spring.support.TestApplication;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = TestApplication.class)
class AuditRecorderIntegrationTest extends AbstractPostgresAndMongoIntegrationTest {

    @Autowired
    private AuditRecorder auditRecorder;

    @Autowired
    private AuditVerifier auditVerifier;

    @Autowired
    private PartitionRegistry partitionRegistry;

    @Autowired
    private EventStore eventStore;

    @Autowired
    private SpringDataAuditEventMongoRepository mongoRepository;

    @Autowired
    private Clock clock;

    @Test
    void recordedEventsAgreeAcrossBothStoresAndVerifyOk() {
        String partitionA = "recorder-test-a-" + UUID.randomUUID();
        String partitionB = "recorder-test-b-" + UUID.randomUUID();

        List<ChainedRecord> recordsA = recordEvents(partitionA, 6);
        List<ChainedRecord> recordsB = recordEvents(partitionB, 5);

        assertAgreement(partitionA, recordsA);
        assertAgreement(partitionB, recordsB);

        PartitionContext contextA = partitionRegistry.resolve(partitionA);
        VerificationResult resultA = auditVerifier.verify(partitionA, contextA);
        assertThat(resultA.status()).isEqualTo(AuditChainStatus.OK);

        PartitionContext contextB = partitionRegistry.resolve(partitionB);
        VerificationResult resultB = auditVerifier.verify(partitionB, contextB);
        assertThat(resultB.status()).isEqualTo(AuditChainStatus.OK);
    }

    private List<ChainedRecord> recordEvents(String partitionKey, int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> auditRecorder.record(buildEvent(partitionKey, i)))
                .toList();
    }

    private void assertAgreement(String partitionKey, List<ChainedRecord> records) {
        List<AuditEvent> stored = eventStore.findByPartitionKey(partitionKey);
        assertThat(stored).hasSize(records.size());

        for (ChainedRecord record : records) {
            AuditEvent matchingStoredEvent = stored.stream()
                    .filter(e -> e.id().equals(record.event().id()))
                    .findFirst()
                    .orElseThrow();
            assertThat(matchingStoredEvent.payload()).isEqualTo(record.event().payload());

            AuditEventDocument document = mongoRepository.findById(record.event().id()).orElseThrow();
            assertThat(document.getSeq()).isEqualTo(record.seq());
            assertThat(document.getEventHash()).isEqualTo(record.eventHash().hex());
            assertThat(document.getPayloadHash()).isEqualTo(record.payloadHash().hex());
        }
    }

    private AuditEvent buildEvent(String partitionKey, int index) {
        return AuditEvent.builder()
                .id(UUID.randomUUID().toString())
                .partitionKey(partitionKey)
                .eventType("RECORDER_TEST_EVENT")
                .actorType(ActorType.SERVICE)
                .actorId("actor-1")
                .category(AuditCategory.DATA_MUTATION)
                .occurredAt(clock.instant())
                .payload(Map.of("index", index))
                .build();
    }
}
