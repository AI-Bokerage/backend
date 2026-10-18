package com.company.ppmsvc.addonprice.model;

import com.company.ppmsvc.common.AuditableEntity;
import com.company.ppmsvc.common.BillingCycle;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

/**
 * {@code AddOnPrice} catalog entity.
 *
 * <p>Represents a versioned, region- and currency-specific price for a purchasable
 * add-on.  The persistence strategy mirrors {@link PlanPrice}: a partial unique
 * index on {@code (addOnId, region, currency, cycle, effectiveFrom) WHERE deleted_at IS NULL}
 * enforces that only one active price exists per business key, while allowing
 * historical rows to accumulate as price versions.
 *
 * <p><strong>Framework independence:</strong> no JPA, Spring, or other
 * infrastructure annotations appear in this class.
 */
@Getter
public class AddOnPrice extends AuditableEntity {

    /** The add-on this price belongs to. */
    private final UUID addOnId;

    /** Billing cycle this price applies to. */
    private final BillingCycle cycle;

    /** ISO 4217 three-letter currency code (e.g. {@code "INR"}, {@code "USD"}). */
    private final String currency;

    /** Market / geographic region identifier (e.g. {@code "INDIA"}, {@code "US"}). */
    private final String region;

    /** Price amount with up to 4 decimal places. */
    private final BigDecimal amount;

    /** Whether the amount already includes applicable taxes. */
    private final boolean taxInclusive;

    /** The date from which this price row becomes the applicable price. */
    private final LocalDate effectiveFrom;

    /** Whether this price row is currently active. */
    private final boolean active;

    @Builder
    private AddOnPrice(UUID id, Long version,
                       Instant createdAt, Instant updatedAt, UUID createdBy, UUID updatedBy,
                       UUID addOnId, BillingCycle cycle, String currency, String region,
                       BigDecimal amount, boolean taxInclusive,
                       LocalDate effectiveFrom, boolean active) {
        super(id, createdAt, updatedAt, createdBy, updatedBy);
        this.version       = version;
        this.addOnId       = addOnId;
        this.cycle         = cycle;
        this.currency      = currency;
        this.region        = region;
        this.amount        = amount;
        this.taxInclusive  = taxInclusive;
        this.effectiveFrom = effectiveFrom;
        this.active        = active;
    }
}
