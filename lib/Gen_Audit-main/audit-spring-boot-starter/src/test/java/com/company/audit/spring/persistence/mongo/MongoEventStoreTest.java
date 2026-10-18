package com.company.audit.spring.persistence.mongo;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.audit.core.api.AuditEvent;
import com.company.audit.core.api.ChainedRecord;
import com.company.audit.core.api.HashValue;
import com.company.audit.core.api.enums.ActorType;
import com.company.audit.core.api.enums.AuditCategory;
import com.company.audit.spring.persistence.mongo.adapter.MongoEventStore;
import com.company.audit.spring.persistence.mongo.document.AuditEventDocument;
import com.company.audit.spring.support.AbstractPostgresAndMongoIntegrationTest;
import com.company.audit.spring.support.TestApplication;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;

@SpringBootTest(classes = TestApplication.class)
class MongoEventStoreTest extends AbstractPostgresAndMongoIntegrationTest {

    @Autowired
    private MongoEventStore eventStore;

    @Autowired
    private MongoTemplate mongoTemplate;

    /**
     * {@code @Indexed} is source-level metadata only — Spring Data Mongo does nothing with it at
     * all unless either {@code spring.data.mongodb.auto-index-creation=true} is set on the
     * *consuming* application, or something explicitly resolves and creates the indexes itself.
     * This queries the real, running Mongo instance's actual index catalog via
     * {@code listIndexes()} rather than trusting the annotation's presence in source — see
     * {@link AuditEventDocument}'s {@code partitionKey}/{@code eventHash} fields, and
     * {@code AuditMongoAutoConfiguration#auditEventDocumentIndexInitializer}, which is what
     * actually creates them here.
     */
    @Test
    void indexedFieldsHaveRealIndexesInTheRunningDatabase() {
        List<String> indexedFieldNames = new java.util.ArrayList<>();
        for (Document index : mongoTemplate.getCollection("audit_events").listIndexes()) {
            Document key = index.get("key", Document.class);
            indexedFieldNames.addAll(key.keySet());
        }
        assertThat(indexedFieldNames).contains("partitionKey", "eventHash");
    }

    @Test
    void saveAndRetrieveRoundTripPreservesThePayloadExactly() throws Exception {
        String partitionKey = "mongo-event-store-test-" + UUID.randomUUID();
        Map<String, Object> payload = Map.of(
                "nested", Map.of("a", 1, "b", List.of(1, 2, 3)),
                "unicode", "漢字 mrhbا 🎉");

        AuditEvent event = buildEvent(partitionKey, payload);
        ChainedRecord record = buildRecord(event);

        eventStore.save(event, record);
        List<AuditEvent> found = eventStore.findByPartitionKey(partitionKey);

        assertThat(found).hasSize(1);
        assertThat(found.get(0).payload()).isEqualTo(payload);
        assertThat(found.get(0).partitionKey()).isEqualTo(partitionKey);
        assertThat(found.get(0).eventType()).isEqualTo(event.eventType());
    }

    private AuditEvent buildEvent(String partitionKey, Map<String, Object> payload) {
        return AuditEvent.builder()
                .id(UUID.randomUUID().toString())
                .partitionKey(partitionKey)
                .eventType("MONGO_TEST_EVENT")
                .actorType(ActorType.SERVICE)
                .actorId("actor-1")
                .category(AuditCategory.DATA_MUTATION)
                .occurredAt(Instant.parse("2024-06-01T12:00:00Z"))
                .payload(payload)
                .build();
    }

    private ChainedRecord buildRecord(AuditEvent event) throws Exception {
        HashValue hash = HashValue.of(MessageDigest.getInstance("SHA-256").digest("test".getBytes()));
        return new ChainedRecord(event, 1L, hash, hash, hash, Instant.parse("2024-06-01T12:00:00Z"));
    }
}
