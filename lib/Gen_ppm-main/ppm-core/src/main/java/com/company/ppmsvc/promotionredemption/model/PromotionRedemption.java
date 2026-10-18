package com.company.ppmsvc.promotionredemption.model;

import com.company.ppmsvc.common.AuditableEntity;
import com.company.ppmsvc.promotion.model.PromotionAction;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

/**
 * A record of a customer redeeming a promotion — the per-user usage ledger.
 *
 * <p>{@code customerId} is a caller-supplied string (PPM does not own
 * customer identity). {@code appliedAction} is a snapshot of the action that
 * was actually applied, stored as JSONB — a promotion's action may change
 * after this row is written, and the ledger must reflect what happened at
 * redemption time, not the promotion's current state.
 *
 * <p><strong>Framework independence:</strong> no JPA, Spring, or other
 * infrastructure annotations appear in this class.
 */
@Getter
public class PromotionRedemption extends AuditableEntity {

    private final UUID promotionId;
    private final String customerId;
    private final UUID planId;
    private final Instant redeemedAt;
    private final PromotionAction appliedAction;
    private final BigDecimal discountAmount;

    @Builder
    private PromotionRedemption(UUID id, Long version,
                                Instant createdAt, Instant updatedAt, UUID createdBy, UUID updatedBy,
                                UUID promotionId, String customerId, UUID planId, Instant redeemedAt,
                                PromotionAction appliedAction, BigDecimal discountAmount) {
        super(id, createdAt, updatedAt, createdBy, updatedBy);
        this.version        = version;
        this.promotionId    = promotionId;
        this.customerId     = customerId;
        this.planId         = planId;
        this.redeemedAt     = redeemedAt;
        this.appliedAction  = appliedAction;
        this.discountAmount = discountAmount;
    }
}
