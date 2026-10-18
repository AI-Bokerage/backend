package com.company.bsmsvc.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "subscription_history")
public class SubscriptionHistoryEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscription_id", nullable = false)
    private SubscriptionEntity subscription;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "action", nullable = false, length = 64)
    private String action;

    @Column(name = "from_plan_version_id")
    private UUID fromPlanVersionId;

    @Column(name = "to_plan_version_id")
    private UUID toPlanVersionId;

    @Column(name = "reason", length = 512)
    private String reason;

    @Column(name = "performed_by", length = 128)
    private String performedBy;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "actor_type", length = 32)
    private String actorType;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}
