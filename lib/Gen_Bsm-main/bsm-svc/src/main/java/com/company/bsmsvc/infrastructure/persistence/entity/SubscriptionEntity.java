package com.company.bsmsvc.infrastructure.persistence.entity;

import com.company.bsmsvc.domain.enums.BillingCycle;
import com.company.bsmsvc.domain.enums.DunningStatus;
import com.company.bsmsvc.domain.enums.PaymentProvider;
import com.company.bsmsvc.domain.enums.SubscriptionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
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
@Table(name = "subscriptions")
public class SubscriptionEntity extends BaseAuditableEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "plan_version_id")
    private UUID planVersionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private SubscriptionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", nullable = false, length = 32)
    private BillingCycle billingCycle;

    @Column(name = "current_period_start", nullable = false)
    private Instant currentPeriodStart;

    @Column(name = "current_period_end", nullable = false)
    private Instant currentPeriodEnd;

    @Column(name = "trial_ends_at")
    private Instant trialEndsAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancel_at_period_end", nullable = false)
    private boolean cancelAtPeriodEnd;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_provider", length = 50)
    private PaymentProvider paymentProvider;

    @Column(name = "external_subscription_id", length = 255)
    private String externalSubscriptionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "dunning_status", length = 50)
    private DunningStatus dunningStatus;

    @Column(name = "dunning_started_at")
    private Instant dunningStartedAt;

    @Column(name = "dunning_next_action_at")
    private Instant dunningNextActionAt;

    // C2: PPM catalog identifiers locked at checkout time for grandfathering.
    @Column(name = "ppm_plan_id")
    private UUID ppmPlanId;

    @Column(name = "ppm_price_id")
    private UUID ppmPriceId;

    @Column(name = "ppm_plan_version_id")
    private UUID ppmPlanVersionId;

    @Column(name = "ppm_resolved_price_minor")
    private Long ppmResolvedPriceMinor;
}
