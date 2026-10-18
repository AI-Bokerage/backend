package com.example.authsvc.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
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
 * JPA entity for the {@code platform_super_admin} table.
 *
 * <p>Stores exactly one platform-level super admin account, bootstrapped on
 * first startup by
 * {@link com.example.authsvc.infrastructure.seed.BootstrapSuperAdminInitializer}
 * when {@code app.super-admin.enabled=true}. Intentionally separate from
 * {@code auth_users} so platform credentials are never co-mingled with tenant
 * user data.
 *
 * <p>Its tenant context in JWTs uses the shared
 * {@link com.example.authsvc.domain.TenantConstants#PLATFORM_TENANT_ID} sentinel
 * — not a locally-declared constant.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "platform_super_admin")
public class PlatformSuperAdminEntity {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "active", nullable = false)
    private boolean active;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
