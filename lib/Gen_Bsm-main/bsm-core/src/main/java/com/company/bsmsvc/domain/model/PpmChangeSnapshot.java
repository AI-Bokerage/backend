package com.company.bsmsvc.domain.model;

import com.company.bsmsvc.domain.enums.PpmPlanChangeType;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Immutable record of a PPM-backed subscription plan change.
 * Written once per {@code applyChange()} call; never updated.
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class PpmChangeSnapshot {

    private UUID id;
    private UUID subscriptionId;
    private UUID tenantId;
    private PpmPlanChangeType changeType;

    private UUID fromPpmPlanId;
    private UUID fromPpmPriceId;
    private UUID fromPpmPlanVersionId;
    private Long fromPpmResolvedPriceMinor;

    private UUID toPpmPlanId;
    private UUID toPpmPriceId;
    private UUID toPpmPlanVersionId;
    private Long toPpmResolvedPriceMinor;

    private Long prorationCreditMinor;
    private Long prorationChargeMinor;
    private Long prorationNetMinor;

    private UUID invoiceId;

    private String appliedPromoCode;
    private Long promoDiscountMinor;

    private Instant changedAt;
    private String changedBy;
    private String reason;
}
