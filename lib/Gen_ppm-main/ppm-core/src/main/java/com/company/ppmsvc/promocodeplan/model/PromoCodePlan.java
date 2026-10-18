package com.company.ppmsvc.promocodeplan.model;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

/**
 * Represents a plan restriction on a promo code.
 *
 * <p>When at least one {@link PromoCodePlan} row exists for a given
 * {@code promoCodeId}, the promo code is restricted to only those plans.
 * An unrestricted promo code has no rows in this table and applies to all plans.
 *
 * <p>Rows are immutable after creation; the only mutation is hard deletion.
 * There is therefore no {@code updatedAt} / {@code updatedBy} on this model.
 *
 * <p><strong>Framework independence:</strong> no JPA, Spring, or other
 * infrastructure annotations appear in this class.
 */
@Getter
public class PromoCodePlan {

    private final UUID    id;
    private final UUID    promoCodeId;
    private final UUID    planId;
    private final Instant createdAt;
    private final UUID    createdBy;

    @Builder
    public PromoCodePlan(UUID id, UUID promoCodeId, UUID planId,
                         Instant createdAt, UUID createdBy) {
        this.id          = id;
        this.promoCodeId = promoCodeId;
        this.planId      = planId;
        this.createdAt   = createdAt;
        this.createdBy   = createdBy;
    }
}
