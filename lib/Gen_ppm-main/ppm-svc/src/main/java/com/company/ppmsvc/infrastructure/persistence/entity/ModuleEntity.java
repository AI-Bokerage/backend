package com.company.ppmsvc.infrastructure.persistence.entity;

import com.company.ppmsvc.module.model.ModuleCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * JPA entity for the {@code ppm_modules} table.
 *
 * <p>Inherits identity, version, audit, and soft-delete columns from
 * {@link JpaBaseEntity}.  Only module-specific columns are declared here.
 *
 * <p>This class must not cross the domain boundary — use
 * {@link com.company.ppmsvc.infrastructure.persistence.mapper.ModulePersistenceMapper}
 * to convert to/from the {@link com.company.ppmsvc.module.model.Module} domain model.
 *
 * <p>{@link com.company.ppmsvc.infrastructure.persistence.converter.ModuleCodeConverter}
 * is applied automatically via {@code @Converter(autoApply = true)}, storing the
 * stable wire value (e.g. {@code "lead_management"}) in the {@code code} column.
 */
@Entity
@Table(name = "ppm_modules")
@org.hibernate.annotations.SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class ModuleEntity extends JpaBaseEntity {

    /** Stable canonical identifier — stored via {@code ModuleCodeConverter}. */
    @Column(name = "code", nullable = false, unique = true, length = 100)
    private ModuleCode code;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active;
}
