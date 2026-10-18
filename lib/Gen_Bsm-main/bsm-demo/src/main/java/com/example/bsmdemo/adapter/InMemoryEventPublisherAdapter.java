package com.example.bsmdemo.adapter;

import com.company.bsmsvc.domain.port.EventPublisherPort;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class InMemoryEventPublisherAdapter implements EventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(InMemoryEventPublisherAdapter.class);

    public record PublishedEvent(String eventType, UUID tenantId, String aggregateType, UUID aggregateId,
                                  UUID actorId, Map<String, Object> data) {}

    private final List<PublishedEvent> published = new CopyOnWriteArrayList<>();

    @Override
    public void publish(String eventType, UUID tenantId, String aggregateType, UUID aggregateId,
                         UUID actorId, Map<String, Object> data) {
        log.info("Event published: type={} tenant={} aggregate={}:{} actor={} data={}",
            eventType, tenantId, aggregateType, aggregateId, actorId, data);
        published.add(new PublishedEvent(eventType, tenantId, aggregateType, aggregateId, actorId, data));
    }

    public List<PublishedEvent> getPublished() {
        return published;
    }
}
