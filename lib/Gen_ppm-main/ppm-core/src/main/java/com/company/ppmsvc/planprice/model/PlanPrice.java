package com.company.ppmsvc.planprice.model;

import com.company.ppmsvc.common.AuditableEntity;
import com.company.ppmsvc.common.BillingCycle;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

/**
 * A single region-aware, cycle-specific price entry for a subscription plan.
 *
 * <p>The business key is {@code (planId, region, currency, cycle, effectiveFrom)}.
 * One row equals one billing cycle — monthly and annual prices are stored as
 * separate rows, which makes versioning, grandfathering, and effective-dated
 * pricing straightforward.
 *
 * <p><strong>Framework independence:</strong> no JPA, Spring, or other
 * infrastructure annotations appear in this class.
 */
@Getter
public class PlanPrice extends AuditableEntity {

    /** Reference to the owning plan. */
    private final UUID planId;

    /** Billing recurrence — {@code MONTHLY} or {@code ANNUAL}. */
    private final BillingCycle cycle;

    /**
     * ISO 4217 three-letter currency code (e.g. {@code "INR"}, {@code "USD"}).
     * Stored uppercase; validation is the caller's responsibility.
     */
    private final String currency;

    /**
     * Market / geographic region (e.g. {@code "INDIA"}, {@code "US"}).
     * Stored as a free-form string so new regions can be added without a migration.
     */
    private final String region;

    /**
     * Price amount in the given currency.
     * Uses {@link BigDecimal} to avoid floating-point representation errors.
     * Stored as {@code NUMERIC(19,4)} in the database.
     */
    private final BigDecimal amount;

    /**
     * Whether the {@code amount} already includes applicable taxes.
     * {@code false} means tax is added on top at checkout.
     */
    private final boolean taxInclusive;

    /**
     * The date from which this price row becomes effective.
     * Multiple rows for the same {@code (planId, region, currency, cycle)} with
     * different {@code effectiveFrom} dates support future-dated price changes.
     */
    private final LocalDate effectiveFrom;

    /**
     * Whether this price row is currently active.
     * Inactive rows are retained for history but excluded from catalog queries.
     */
    private final boolean active;

    @Builder
    private PlanPrice(UUID id, Long version,
                      Instant createdAt, Instant updatedAt, UUID createdBy, UUID updatedBy,
                      UUID planId, BillingCycle cycle, String currency, String region,
                      BigDecimal amount, boolean taxInclusive,
                      LocalDate effectiveFrom, boolean active) {
        super(id, createdAt, updatedAt, createdBy, updatedBy);
        this.version       = version;
        this.planId        = planId;
        this.cycle         = cycle;
        this.currency      = currency;
        this.region        = region;
        this.amount        = amount;
        this.taxInclusive  = taxInclusive;
        this.effectiveFrom = effectiveFrom;
        this.active        = active;
    }
}
