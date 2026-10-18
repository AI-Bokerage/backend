package com.company.ppmsvc.infrastructure.persistence.entity;

import com.company.ppmsvc.common.BillingCycle;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * JPA entity for the {@code ppm_plan_prices} table.
 *
 * <p>Inherits identity, version, audit, and soft-delete columns from
 * {@link JpaBaseEntity}.  Only plan-price-specific columns are declared here.
 *
 * <p>This class must not cross the domain boundary — use
 * {@link com.company.ppmsvc.infrastructure.persistence.mapper.PlanPricePersistenceMapper}
 * to convert to/from the {@link com.company.ppmsvc.planprice.model.PlanPrice} domain model.
 *
 * <p>{@link com.company.ppmsvc.infrastructure.persistence.converter.BillingCycleConverter}
 * is applied automatically via {@code @Converter(autoApply = true)}, storing the
 * stable wire value (e.g. {@code "monthly"}, {@code "annual"}) in the {@code cycle} column.
 */
@Entity
@Table(name = "ppm_plan_prices")
@org.hibernate.annotations.SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class PlanPriceEntity extends JpaBaseEntity {

    @Column(name = "plan_id", nullable = false, updatable = false)
    private UUID planId;

    /** Stored via {@code BillingCycleConverter} as its wire value. */
    @Column(name = "cycle", nullable = false, length = 32)
    private BillingCycle cycle;

    /** ISO 4217 three-letter currency code. */
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    /** Market / geographic region identifier. */
    @Column(name = "region", nullable = false, length = 32)
    private String region;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "tax_inclusive", nullable = false)
    private boolean taxInclusive;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "active", nullable = false)
    private boolean active;
}
