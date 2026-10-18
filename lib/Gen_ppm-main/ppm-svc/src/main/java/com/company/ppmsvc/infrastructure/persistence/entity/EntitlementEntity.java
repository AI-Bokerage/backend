package com.company.ppmsvc.infrastructure.persistence.entity;

import com.company.ppmsvc.entitlement.model.EntitlementType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * JPA entity for the {@code ppm_entitlements} table.
 *
 * <p>Inherits identity, version, audit, and soft-delete columns from
 * {@link JpaBaseEntity}.  Only entitlement-specific columns are declared here.
 *
 * <p>This class must not cross the domain boundary — use
 * {@link com.company.ppmsvc.infrastructure.persistence.mapper.EntitlementPersistenceMapper}
 * to convert to/from the {@link com.company.ppmsvc.entitlement.model.Entitlement} domain model.
 *
 * <p>{@link com.company.ppmsvc.infrastructure.persistence.converter.EntitlementTypeConverter}
 * is applied automatically via {@code @Converter(autoApply = true)}, storing the
 * stable wire value (e.g. {@code "quota"}) in the {@code type} column.
 */
@Entity
@Table(name = "ppm_entitlements")
@org.hibernate.annotations.SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class EntitlementEntity extends JpaBaseEntity {

    /** Stable machine-readable identifier — stored as a plain VARCHAR. */
    @Column(name = "code", nullable = false, length = 100)
    private String code;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** Stored as a VARCHAR wire value via {@code EntitlementTypeConverter}. */
    @Column(name = "type", nullable = false, length = 32)
    private EntitlementType type;

    @Column(name = "active", nullable = false)
    private boolean active;
}
