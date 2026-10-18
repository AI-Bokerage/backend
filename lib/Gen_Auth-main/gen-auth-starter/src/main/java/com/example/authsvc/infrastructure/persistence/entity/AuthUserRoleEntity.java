package com.example.authsvc.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * AUTH-SVC read projection of ADM-SVC's user_roles table.
 *
 * <p>Each row records one ADM role assignment for an internal user.
 * A user may have multiple rows (multi-role model).</p>
 *
 * <p>Written by {@code RegisterServiceImpl} when a non-null {@code roleId} is
 * supplied at user creation (internal admin-provisioning path only, via
 * {@code /internal/auth/users}). Self-registered users get no row here.</p>
 *
 * <p>Nothing in the current codebase deletes rows from this table.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
    name = "auth_user_roles",
    indexes = {
        @Index(name = "idx_aur_user_tenant", columnList = "user_id, tenant_id")
    }
)
public class AuthUserRoleEntity {

    @EmbeddedId
    private AuthUserRoleId id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @CreationTimestamp
    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;
}
