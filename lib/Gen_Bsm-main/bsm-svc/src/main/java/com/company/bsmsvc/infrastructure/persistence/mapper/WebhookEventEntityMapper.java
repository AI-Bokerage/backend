package com.company.bsmsvc.infrastructure.persistence.mapper;

import com.company.bsmsvc.domain.model.WebhookEvent;
import com.company.bsmsvc.infrastructure.persistence.entity.WebhookEventEntity;
import org.springframework.stereotype.Component;

@Component
public class WebhookEventEntityMapper {

    public WebhookEvent toDomain(WebhookEventEntity e) {
        if (e == null) return null;
        return WebhookEvent.builder()
            .id(e.getId()).provider(e.getProvider()).externalEventId(e.getExternalEventId())
            .eventType(e.getEventType()).payload(e.getPayload()).status(e.getStatus())
            .failureReason(e.getFailureReason()).receivedAt(e.getReceivedAt())
            .processedAt(e.getProcessedAt()).build();
    }

    public WebhookEventEntity toEntity(WebhookEvent d) {
        if (d == null) return null;
        return WebhookEventEntity.builder()
            .id(d.getId()).provider(d.getProvider()).externalEventId(d.getExternalEventId())
            .eventType(d.getEventType()).payload(d.getPayload()).status(d.getStatus())
            .failureReason(d.getFailureReason()).receivedAt(d.getReceivedAt())
            .processedAt(d.getProcessedAt()).build();
    }
}
