package com.example.modauth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/** Whether a brokerage's people may sign in (Flow 1 suspend / cancel). See TenantAccessService. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "modauth_tenant_access")
public class TenantAccessEntity {

    @Id
    @Column(name = "tenant_id", updatable = false, nullable = false)
    private UUID tenantId;

    @Column(name = "blocked", nullable = false)
    private boolean blocked;

    /** The brokerage status that caused it: SUSPENDED, CANCELLED or ACTIVE. */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
