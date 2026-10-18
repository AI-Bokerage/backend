package com.example.tnt_svc.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenant")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TenantStatus status;

    private String region;

    @Column(name = "primary_owner_user_id", nullable = false)
    private UUID primaryOwnerUserId;

    @Column(name = "provisioning_job_id")
    private UUID provisioningJobId;

    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @jakarta.persistence.PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @jakarta.persistence.PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void transitionTo(TenantStatus target) {
        TenantStateMachine.validateTransition(status, target);
        status = target;
    }

    public void activateAfterProvisioning() {
        transitionTo(TenantStatus.ACTIVE);
    }

    public void suspend() {
        transitionTo(TenantStatus.SUSPENDED);
    }

    public void reactivate() {
        transitionTo(TenantStatus.ACTIVE);
    }

    public void cancel() {
        transitionTo(TenantStatus.CANCELLED);
    }

    public void purge() {
        transitionTo(TenantStatus.PURGED);
    }
}
