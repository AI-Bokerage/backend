package com.company.ppmsvc.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Shared persistence base for every PPM JPA entity.
 *
 * <p>PPM-SVC is a platform-wide catalog service — no per-tenant row isolation
 * is applied, so there is no {@code tenant_id} column here.
 *
 * <p>Columns present on every table:
 * <ul>
 *   <li>Identity — {@code id}</li>
 *   <li>Optimistic locking — {@code version}</li>
 *   <li>Audit trail — {@code created_at / updated_at / created_by / updated_by}</li>
 *   <li>Soft-delete — {@code deleted_at} (null ⇒ active)</li>
 * </ul>
 */
@MappedSuperclass
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public abstract class JpaBaseEntity {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
