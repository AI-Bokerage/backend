package com.company.bsmsvc.domain.port;

import java.util.Map;
import java.util.UUID;

/**
 * Publishes a billing business event that occurred as part of a use case
 * (invoice paid, refund completed, dunning started, etc.). The library only
 * knows that the event happened — it never knows what happens after
 * publication (outbox, RabbitMQ, Kafka, audit consumption, monitoring all
 * remain host decisions).
 */
public interface EventPublisherPort {

    void publish(String eventType, UUID tenantId, String aggregateType, UUID aggregateId,
                 UUID actorId, Map<String, Object> data);
}
