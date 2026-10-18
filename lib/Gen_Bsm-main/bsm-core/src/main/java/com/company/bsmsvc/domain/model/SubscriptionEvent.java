package com.company.bsmsvc.domain.model;

import com.company.bsmsvc.domain.enums.ActorType;
import com.company.bsmsvc.domain.enums.SubscriptionEventType;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionEvent {

    private UUID id;
    private UUID subscriptionId;
    private UUID tenantId;
    private SubscriptionEventType eventType;
    private Map<String, Object> payload;
    private int eventVersion;
    private UUID actorId;
    private ActorType actorType;
    private Instant occurredAt;
    private Instant createdAt;
}
