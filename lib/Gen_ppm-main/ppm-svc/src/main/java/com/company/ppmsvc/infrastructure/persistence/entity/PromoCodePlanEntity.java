package com.company.ppmsvc.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA entity for the {@code ppm_promo_code_plans} join table.
 *
 * <p>Deliberately does NOT extend {@link JpaBaseEntity} — the restriction table
 * has a reduced column set (no {@code version}, {@code updated_at},
 * {@code updated_by}, {@code deleted_at}) because these rows are create-only;
 * the only mutation is hard deletion.
 *
 * <p>This class must not cross the domain boundary — use
 * {@link com.company.ppmsvc.infrastructure.persistence.mapper.PromoCodePlanPersistenceMapper}
 * to convert to/from the {@link com.company.ppmsvc.promocodeplan.model.PromoCodePlan} domain model.
 */
@Entity
@Table(name = "ppm_promo_code_plans")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromoCodePlanEntity {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "promo_code_id", nullable = false, updatable = false)
    private UUID promoCodeId;

    @Column(name = "plan_id", nullable = false, updatable = false)
    private UUID planId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;
}
