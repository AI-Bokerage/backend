package com.company.ppmsvc.coupon.model;

import com.company.ppmsvc.common.AuditableEntity;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

/**
 * A redemption code pointing to a {@link com.company.ppmsvc.promotion.model.Promotion}
 * by UUID FK (no object reference — same pattern as {@code PromoCodePlan}).
 * One promotion may have many coupons.
 *
 * <p><strong>Framework independence:</strong> no JPA, Spring, or other
 * infrastructure annotations appear in this class.
 */
@Getter
public class Coupon extends AuditableEntity {

    /** Redemption code string (e.g. {@code "DIWALI20"}). Immutable after creation. */
    private final String code;

    /** Reference to the owning promotion. */
    private final UUID promotionId;

    /** Whether this coupon is currently active. Inactive coupons cannot be redeemed. */
    private final Boolean active;

    @Builder
    private Coupon(UUID id, Long version,
                   Instant createdAt, Instant updatedAt, UUID createdBy, UUID updatedBy,
                   String code, UUID promotionId, Boolean active) {
        super(id, createdAt, updatedAt, createdBy, updatedBy);
        this.version     = version;
        this.code        = code;
        this.promotionId = promotionId;
        this.active      = active;
    }
}
