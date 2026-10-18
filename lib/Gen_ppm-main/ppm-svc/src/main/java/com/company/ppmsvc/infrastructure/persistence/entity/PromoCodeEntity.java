package com.company.ppmsvc.infrastructure.persistence.entity;

import com.company.ppmsvc.promocode.model.DiscountType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * JPA entity for the {@code ppm_promo_codes} table.
 *
 * <p>Inherits identity, version, audit, and soft-delete columns from
 * {@link JpaBaseEntity}.  Only promo-code-specific columns are declared here.
 *
 * <p>This class must not cross the domain boundary — use
 * {@link com.company.ppmsvc.infrastructure.persistence.mapper.PromoCodePersistenceMapper}
 * to convert to/from the {@link com.company.ppmsvc.promocode.model.PromoCode} domain model.
 *
 * <p>{@link com.company.ppmsvc.infrastructure.persistence.converter.DiscountTypeConverter}
 * is applied automatically via {@code @Converter(autoApply = true)}, storing the
 * stable wire value (e.g. {@code "percentage"}, {@code "flat"}) in the
 * {@code discount_type} column.
 */
@Entity
@Table(name = "ppm_promo_codes")
@org.hibernate.annotations.SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class PromoCodeEntity extends JpaBaseEntity {

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    /** Stored via {@code DiscountTypeConverter} as its wire value. */
    @Column(name = "discount_type", nullable = false, length = 32)
    private DiscountType discountType;

    @Column(name = "value", nullable = false, precision = 19, scale = 4)
    private BigDecimal value;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_until", nullable = false)
    private LocalDate validUntil;

    @Column(name = "usage_cap")
    private Integer usageCap;

    @Column(name = "usage_count", nullable = false)
    private Integer usageCount;

    @Column(name = "first_time_only", nullable = false)
    private Boolean firstTimeOnly;

    @Column(name = "active", nullable = false)
    private Boolean active;
}
