package com.company.ppmsvc.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA entity for the {@code ppm_plan_add_ons} join table.
 *
 * <p>Deliberately does NOT extend {@link JpaBaseEntity} — the mapping table
 * has a reduced column set (no {@code updated_at}, {@code updated_by},
 * {@code deleted_at}) because these rows are create-only; the only mutation
 * is hard deletion.
 *
 * <p>This class must not cross the domain boundary — use
 * {@link com.company.ppmsvc.infrastructure.persistence.mapper.PlanAddOnPersistenceMapper}
 * to convert to/from the {@link com.company.ppmsvc.planaddon.model.PlanAddOn} domain model.
 */
@Entity
@Table(name = "ppm_plan_add_ons")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanAddOnEntity {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "plan_id", nullable = false, updatable = false)
    private UUID planId;

    @Column(name = "add_on_id", nullable = false, updatable = false)
    private UUID addOnId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;
}
