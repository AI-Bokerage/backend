package com.company.audit.spring.wire;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.audit.core.api.AuditEvent;
import com.company.audit.core.api.enums.ActorType;
import com.company.audit.core.api.enums.AuditCategory;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuditEventEnvelopeMapperTest {

    private final AuditEventEnvelopeMapper mapper = new AuditEventEnvelopeMapper();

    @Test
    void validEnvelopeMapsCorrectly() {
        AuditEventEnvelope envelope = new AuditEventEnvelope(
                AuditEventEnvelope.CURRENT_SCHEMA_VERSION,
                "partition-1",
                "USER_LOGIN",
                "SERVICE",
                "actor-1",
                "DATA_MUTATION",
                Instant.parse("2024-06-01T12:00:00Z"),
                Map.of("ip", "127.0.0.1"),
                "resource-type",
                "resource-1",
                "a reason",
                Map.of("traceId", "abc-123"));

        AuditEvent event = mapper.toDomain(envelope);

        assertThat(event.partitionKey()).isEqualTo("partition-1");
        assertThat(event.eventType()).isEqualTo("USER_LOGIN");
        assertThat(event.actorType()).isEqualTo(ActorType.SERVICE);
        assertThat(event.actorId()).isEqualTo("actor-1");
        assertThat(event.category()).isEqualTo(AuditCategory.DATA_MUTATION);
        assertThat(event.occurredAt()).isEqualTo(Instant.parse("2024-06-01T12:00:00Z"));
        assertThat(event.payload()).isEqualTo(Map.of("ip", "127.0.0.1"));
        assertThat(event.resourceType()).isEqualTo("resource-type");
        assertThat(event.resourceId()).isEqualTo("resource-1");
        assertThat(event.reason()).isEqualTo("a reason");
        assertThat(event.id()).isNotBlank();
    }

    @Test
    void unrecognizedActorTypeThrowsUnrecognizedEnvelopeValueException() {
        AuditEventEnvelope envelope = new AuditEventEnvelope(
                AuditEventEnvelope.CURRENT_SCHEMA_VERSION,
                "partition-1",
                "USER_LOGIN",
                "NOT_A_REAL_ACTOR_TYPE",
                "actor-1",
                "DATA_MUTATION",
                Instant.parse("2024-06-01T12:00:00Z"),
                Map.of(),
                null,
                null,
                null,
                Map.of());

        assertThatThrownBy(() -> mapper.toDomain(envelope))
                .isInstanceOf(UnrecognizedEnvelopeValueException.class)
                .hasMessageContaining("actorType")
                .hasMessageContaining("NOT_A_REAL_ACTOR_TYPE");
    }

    @Test
    void unrecognizedCategoryThrowsUnrecognizedEnvelopeValueException() {
        AuditEventEnvelope envelope = new AuditEventEnvelope(
                AuditEventEnvelope.CURRENT_SCHEMA_VERSION,
                "partition-1",
                "USER_LOGIN",
                "SERVICE",
                "actor-1",
                "NOT_A_REAL_CATEGORY",
                Instant.parse("2024-06-01T12:00:00Z"),
                Map.of(),
                null,
                null,
                null,
                Map.of());

        assertThatThrownBy(() -> mapper.toDomain(envelope))
                .isInstanceOf(UnrecognizedEnvelopeValueException.class)
                .hasMessageContaining("category")
                .hasMessageContaining("NOT_A_REAL_CATEGORY");
    }
}
