package com.company.bsmsvc.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "proration_previews")
public class ProrationPreviewEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscription_id", nullable = false)
    private SubscriptionEntity subscription;

    @Column(name = "from_plan_version_id", nullable = false)
    private UUID fromPlanVersionId;

    @Column(name = "to_plan_version_id", nullable = false)
    private UUID toPlanVersionId;

    @Column(name = "proration_mode", nullable = false, length = 32)
    private String prorationMode;

    @Column(name = "current_plan_credit_minor", nullable = false)
    private Long currentPlanCreditMinor;

    @Column(name = "target_plan_charge_minor", nullable = false)
    private Long targetPlanChargeMinor;

    @Column(name = "prorated_amount_minor", nullable = false)
    private Long proratedAmountMinor;

    @Column(name = "currency", nullable = false, length = 8)
    private String currency;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "breakdown", columnDefinition = "jsonb")
    private Map<String, Object> breakdown;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
