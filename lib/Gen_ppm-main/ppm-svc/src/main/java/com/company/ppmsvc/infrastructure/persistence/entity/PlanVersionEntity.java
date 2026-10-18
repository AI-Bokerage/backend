package com.company.ppmsvc.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * JPA entity for the {@code ppm_plan_versions} table.
 *
 * <p>Inherits identity, version, audit, and soft-delete columns from
 * {@link JpaBaseEntity}.  Only plan-version-specific columns are declared here.
 *
 * <p>This class must not cross the domain boundary — use
 * {@link com.company.ppmsvc.infrastructure.persistence.mapper.PlanVersionPersistenceMapper}
 * to convert to/from the {@link com.company.ppmsvc.plan.model.PlanVersion} domain model.
 */
@Entity
@Table(name = "ppm_plan_versions")
@org.hibernate.annotations.SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class PlanVersionEntity extends JpaBaseEntity {

    @Column(name = "plan_id", nullable = false, updatable = false)
    private UUID planId;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "active", nullable = false)
    private boolean active;

    // ── Limit fields (PPM-12B) ────────────────────────────────────────────────
    // Null = unlimited for numeric caps; null = false for boolean feature flags.

    @Column(name = "max_internal_users")
    private Integer maxInternalUsers;

    @Column(name = "max_client_users")
    private Integer maxClientUsers;

    @Column(name = "max_active_projects")
    private Integer maxActiveProjects;

    @Column(name = "storage_quota_bytes")
    private Long storageQuotaBytes;

    @Column(name = "custom_domain_enabled")
    private Boolean customDomainEnabled;

    @Column(name = "sso_enabled")
    private Boolean ssoEnabled;

    @Column(name = "priority_support")
    private Boolean prioritySupport;
}
