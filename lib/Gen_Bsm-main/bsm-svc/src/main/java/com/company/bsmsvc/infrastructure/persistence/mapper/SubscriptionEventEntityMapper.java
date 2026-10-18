package com.company.bsmsvc.infrastructure.persistence.mapper;

import com.company.bsmsvc.domain.enums.ActorType;
import com.company.bsmsvc.domain.enums.SubscriptionEventType;
import com.company.bsmsvc.domain.model.SubscriptionEvent;
import com.company.bsmsvc.infrastructure.persistence.entity.SubscriptionEventEntity;
import java.util.Collections;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionEventEntityMapper {

    public SubscriptionEvent toDomain(SubscriptionEventEntity entity) {
        return SubscriptionEvent.builder()
            .id(entity.getId())
            .subscriptionId(entity.getSubscription().getId())
            .tenantId(entity.getTenantId())
            .eventType(SubscriptionEventType.valueOf(entity.getEventType()))
            .payload(entity.getPayload() == null ? Collections.emptyMap() : entity.getPayload())
            .eventVersion(entity.getEventVersion() == null ? 1 : entity.getEventVersion())
            .actorId(entity.getActorId())
            .actorType(entity.getActorType() == null ? null : ActorType.valueOf(entity.getActorType()))
            .occurredAt(entity.getOccurredAt())
            .createdAt(entity.getCreatedAt())
            .build();
    }

    public SubscriptionEventEntity toEntity(SubscriptionEvent domain) {
        SubscriptionEventEntity entity = new SubscriptionEventEntity();
        entity.setId(domain.getId());
        entity.setTenantId(domain.getTenantId());
        entity.setEventType(domain.getEventType().name());
        entity.setPayload(domain.getPayload() == null ? Collections.emptyMap() : domain.getPayload());
        entity.setEventVersion(domain.getEventVersion() < 1 ? 1 : domain.getEventVersion());
        entity.setActorId(domain.getActorId());
        entity.setActorType(domain.getActorType() == null ? null : domain.getActorType().name());
        entity.setOccurredAt(domain.getOccurredAt());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setSubscription(com.company.bsmsvc.infrastructure.persistence.entity.SubscriptionEntity.builder().id(domain.getSubscriptionId()).build());
        return entity;
    }
}
