package com.company.ppmsvc.promocode.model;

import com.company.ppmsvc.common.AuditableEntity;

import com.company.ppmsvc.promocode.model.DiscountType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

/**
 * A promo code catalog entry.
 *
 * <p>Captures the discount definition, validity window, usage limits, and
 * activation state.  Plan-level restrictions are stored separately in
 * {@link PromoCodePlan} rows linked via {@code id}.
 *
 * <p><strong>Framework independence:</strong> no JPA, Spring, or other
 * infrastructure annotations appear in this class.
 */
@Getter
public class PromoCode extends AuditableEntity {

    /** Human-readable code string (e.g. {@code "SUMMER20"}). Unique among active rows. */
    private final String code;

    /** Whether the discount is percentage-based or a flat monetary amount. */
    private final DiscountType discountType;

    /**
     * The discount magnitude.
     * For {@link DiscountType#PERCENTAGE}: a value between 0 and 100 (e.g. {@code 20.0000}).
     * For {@link DiscountType#FLAT}: an absolute monetary amount in the plan's currency.
     */
    private final BigDecimal value;

    /** The first calendar date on which this code is valid. */
    private final LocalDate validFrom;

    /** The last calendar date on which this code is valid (inclusive). */
    private final LocalDate validUntil;

    /**
     * Maximum total number of times this code may be redeemed across all users.
     * {@code null} means unlimited.
     */
    private final Integer usageCap;

    /** Running count of successful redemptions. Incremented at checkout. */
    private final Integer usageCount;

    /**
     * When {@code true}, only users making their very first subscription are
     * eligible to redeem this code.
     */
    private final Boolean firstTimeOnly;

    /** Whether this code is currently active. Inactive codes cannot be redeemed. */
    private final Boolean active;

    @Builder
    private PromoCode(UUID id, Long version,
                      Instant createdAt, Instant updatedAt, UUID createdBy, UUID updatedBy,
                      String code, DiscountType discountType, BigDecimal value,
                      LocalDate validFrom, LocalDate validUntil,
                      Integer usageCap, Integer usageCount,
                      Boolean firstTimeOnly, Boolean active) {
        super(id, createdAt, updatedAt, createdBy, updatedBy);
        this.version       = version;
        this.code          = code;
        this.discountType  = discountType;
        this.value         = value;
        this.validFrom     = validFrom;
        this.validUntil    = validUntil;
        this.usageCap      = usageCap;
        this.usageCount    = usageCount;
        this.firstTimeOnly = firstTimeOnly;
        this.active        = active;
    }
}
