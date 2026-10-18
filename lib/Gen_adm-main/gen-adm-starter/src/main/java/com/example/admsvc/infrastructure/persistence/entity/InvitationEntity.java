package com.example.admsvc.infrastructure.persistence.entity;

import com.example.admsvc.domain.enums.InvitationStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "invitations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvitationEntity {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InvitationStatus status;

    @Column(name = "invited_by_user_id", nullable = false)
    private UUID invitedByUserId;

    /**
     * JSON array of role UUIDs to assign when the invitation is accepted,
     * e.g. '["uuid1","uuid2"]'. Plain TEXT column — written once at
     * creation, read once at accept, never queried by role.
     */
    @Column(name = "role_ids", nullable = false, columnDefinition = "TEXT")
    @Builder.Default
    private String roleIdsJson = "[]";

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by_user_id")
    private UUID cancelledByUserId;

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * One-time plaintext token, populated only on the object returned by
     * {@code create()} — never persisted, never retrievable again after
     * that call returns.
     */
    @Transient
    private String plaintextToken;

    public List<UUID> getRoleIds() {
        if (roleIdsJson == null || roleIdsJson.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return MAPPER.readValue(roleIdsJson, new TypeReference<List<UUID>>() {});
        } catch (JsonProcessingException e) {
            return new ArrayList<>();
        }
    }

    public void setRoleIds(List<UUID> roleIds) {
        try {
            this.roleIdsJson = MAPPER.writeValueAsString(roleIds == null ? List.of() : roleIds);
        } catch (JsonProcessingException e) {
            this.roleIdsJson = "[]";
        }
    }

    @PrePersist
    void prePersist() {
        createdAt = updatedAt = Instant.now();
        if (status == null) {
            status = InvitationStatus.PENDING;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
