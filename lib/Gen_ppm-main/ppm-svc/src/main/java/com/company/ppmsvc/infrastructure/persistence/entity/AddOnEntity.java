package com.company.ppmsvc.infrastructure.persistence.entity;

import com.company.ppmsvc.addon.model.AddOnType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * JPA entity for the {@code ppm_add_ons} table.
 *
 * <p>Inherits identity, version, audit, and soft-delete columns from
 * {@link JpaBaseEntity}.  Only add-on-specific columns are declared here.
 *
 * <p>This class must not cross the domain boundary — use
 * {@link com.company.ppmsvc.infrastructure.persistence.mapper.AddOnPersistenceMapper}
 * to convert to/from the {@link com.company.ppmsvc.addon.model.AddOn} domain model.
 *
 * <p>{@link com.company.ppmsvc.infrastructure.persistence.converter.AddOnTypeConverter}
 * is applied automatically via {@code @Converter(autoApply = true)}, storing the
 * stable wire value (e.g. {@code "quota"}) in the {@code type} column.
 */
@Entity
@Table(name = "ppm_add_ons")
@org.hibernate.annotations.SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class AddOnEntity extends JpaBaseEntity {

    @Column(name = "code", nullable = false, length = 100)
    private String code;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** Stored via {@code AddOnTypeConverter} as its wire value. */
    @Column(name = "type", nullable = false, length = 50)
    private AddOnType type;

    @Column(name = "active", nullable = false)
    private boolean active;
}
