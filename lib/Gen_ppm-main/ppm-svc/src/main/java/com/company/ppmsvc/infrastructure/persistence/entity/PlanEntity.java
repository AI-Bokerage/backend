package com.company.ppmsvc.infrastructure.persistence.entity;

import com.company.ppmsvc.plan.model.PlanVisibility;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * JPA entity for the {@code ppm_plans} table.
 *
 * <p>Inherits identity, version, audit, and soft-delete columns from
 * {@link JpaBaseEntity}.  Only plan-specific columns are declared here.
 *
 * <p>This class must not cross the domain boundary — use
 * {@link com.company.ppmsvc.infrastructure.persistence.mapper.PlanPersistenceMapper}
 * to convert to/from the {@link com.company.ppmsvc.plan.model.Plan} domain model.
 *
 * <p>{@link com.company.ppmsvc.infrastructure.persistence.converter.PlanVisibilityConverter}
 * is applied automatically via {@code @Converter(autoApply = true)}, storing the
 * stable wire value (e.g. {@code "public"}) in the {@code visibility} column.
 */
@Entity
@Table(name = "ppm_plans")
@org.hibernate.annotations.SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class PlanEntity extends JpaBaseEntity {

    /** Stable machine-readable identifier (e.g. {@code "starter"}). */
    @Column(name = "code", nullable = false, length = 50)
    private String code;

    /** Human-readable, URL-safe slug — immutable after creation. */
    @Column(name = "slug", nullable = false, length = 100)
    private String slug;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "tagline", length = 500)
    private String tagline;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** Stored via {@code PlanVisibilityConverter} as its wire value. */
    @Column(name = "visibility", nullable = false, length = 20)
    private PlanVisibility visibility;

    @Column(name = "trial_days", nullable = false)
    private Integer trialDays;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "tier", length = 30)
    private String tier;
}
