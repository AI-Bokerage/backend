package com.company.ppmsvc.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * JPA entity for the {@code ppm_coupons} table.
 *
 * <p>Inherits identity, version, audit, and soft-delete columns from
 * {@link JpaBaseEntity}. {@code promotionId} is a plain FK column — no JPA
 * relationship mapping, matching the {@code PromoCodePlan} join pattern.
 */
@Entity
@Table(name = "ppm_coupons")
@org.hibernate.annotations.SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class CouponEntity extends JpaBaseEntity {

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "promotion_id", nullable = false)
    private UUID promotionId;

    @Column(name = "active", nullable = false)
    private Boolean active;
}
