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

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the {@code tenant_settings} table — intentionally minimal,
 * one flag today. This service has no {@code tenants} table by design
 * (tenant data lives elsewhere); this is purely additive local config.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tenant_settings")
public class TenantSettingsEntity {

    @Id
    @Column(name = "tenant_id", updatable = false, nullable = false)
    private UUID tenantId;

    @Column(name = "mfa_required", nullable = false)
    private boolean mfaRequired;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
