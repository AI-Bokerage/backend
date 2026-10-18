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
 * JPA entity for the {@code ppm_add_on_prices} table.
 *
 * <p>Inherits identity, version, audit, and soft-delete columns from
 * {@link JpaBaseEntity}.  Only add-on-price-specific columns are declared here.
 *
 * <p>This class must not cross the domain boundary — use
 * {@link com.company.ppmsvc.infrastructure.persistence.mapper.AddOnPricePersistenceMapper}
 * to convert to/from the {@link com.company.ppmsvc.addonprice.model.AddOnPrice} domain model.
 *
 * <p>{@link com.company.ppmsvc.infrastructure.persistence.converter.BillingCycleConverter}
 * is applied automatically via {@code @Converter(autoApply = true)}, storing the
 * stable wire value (e.g. {@code "monthly"}) in the {@code cycle} column.
 */
@Entity
@Table(name = "ppm_add_on_prices")
@org.hibernate.annotations.SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class AddOnPriceEntity extends JpaBaseEntity {

    @Column(name = "add_on_id", nullable = false, updatable = false)
    private UUID addOnId;

    /** Stored via {@code BillingCycleConverter} as its wire value. */
    @Column(name = "cycle", nullable = false, length = 50)
    private BillingCycle cycle;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    @Column(name = "region", nullable = false, length = 50)
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
