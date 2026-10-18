package com.company.bsmsvc.infrastructure.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Saves a serialized event payload to the BSM outbox table inside the current transaction.
 * The event will be dispatched by {@link BsmOutboxPublisher} on the next polling cycle.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BsmOutboxService {

    private final BsmOutboxJpaRepository repository;
    private final ObjectMapper objectMapper;

    public void save(String aggregateType, UUID aggregateId,
                     String eventType, String routingKey, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            BsmOutboxEventEntity entity = new BsmOutboxEventEntity();
            entity.setAggregateType(aggregateType);
            entity.setAggregateId(aggregateId);
            entity.setEventType(eventType);
            entity.setRoutingKey(routingKey);
            entity.setPayload(json);
            entity.setStatus(BsmOutboxEventStatus.PENDING);
            repository.save(entity);
            log.debug("[BSM-OUTBOX] Saved event eventType={} aggregateId={}", eventType, aggregateId);
        } catch (Exception e) {
            log.error("[BSM-OUTBOX] Failed to save outbox event eventType={} aggregateId={}: {}",
                eventType, aggregateId, e.getMessage(), e);
            throw new RuntimeException("Failed to persist outbox event: " + eventType, e);
        }
    }
}
