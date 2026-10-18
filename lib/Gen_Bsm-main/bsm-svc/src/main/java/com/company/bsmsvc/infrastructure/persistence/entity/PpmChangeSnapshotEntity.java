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
@Table(name = "subscription_ppm_change_snapshots")
public class PpmChangeSnapshotEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscription_id", nullable = false)
    private SubscriptionEntity subscription;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "change_type", nullable = false, length = 32)
    private String changeType;

    @Column(name = "from_ppm_plan_id", nullable = false)
    private UUID fromPpmPlanId;

    @Column(name = "from_ppm_price_id", nullable = false)
    private UUID fromPpmPriceId;

    @Column(name = "from_ppm_plan_version_id", nullable = false)
    private UUID fromPpmPlanVersionId;

    @Column(name = "from_ppm_resolved_price_minor", nullable = false)
    private Long fromPpmResolvedPriceMinor;

    @Column(name = "to_ppm_plan_id", nullable = false)
    private UUID toPpmPlanId;

    @Column(name = "to_ppm_price_id", nullable = false)
    private UUID toPpmPriceId;

    @Column(name = "to_ppm_plan_version_id", nullable = false)
    private UUID toPpmPlanVersionId;

    @Column(name = "to_ppm_resolved_price_minor", nullable = false)
    private Long toPpmResolvedPriceMinor;

    @Column(name = "proration_credit_minor", nullable = false)
    private Long prorationCreditMinor;

    @Column(name = "proration_charge_minor", nullable = false)
    private Long prorationChargeMinor;

    @Column(name = "proration_net_minor", nullable = false)
    private Long prorationNetMinor;

    @Column(name = "invoice_id")
    private UUID invoiceId;

    @Column(name = "applied_promo_code", length = 64)
    private String appliedPromoCode;

    @Column(name = "promo_discount_minor")
    private Long promoDiscountMinor;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @Column(name = "changed_by", length = 128)
    private String changedBy;

    @Column(name = "reason", length = 512)
    private String reason;
}
