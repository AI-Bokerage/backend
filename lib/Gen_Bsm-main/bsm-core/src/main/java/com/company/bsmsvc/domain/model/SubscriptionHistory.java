package com.company.bsmsvc.domain.model;

import com.company.bsmsvc.domain.enums.ActorType;
import com.company.bsmsvc.domain.enums.SubscriptionHistoryAction;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionHistory {

    private UUID id;
    private UUID subscriptionId;
    private UUID tenantId;
    private SubscriptionHistoryAction action;
    private UUID fromPlanVersionId;
    private UUID toPlanVersionId;
    private String reason;
    private String performedBy;
    private UUID actorId;
    private ActorType actorType;
    private Instant occurredAt;
}
