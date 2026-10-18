package com.example.modauth.entity;

import com.example.modauth.domain.InvitationStatus;
import com.example.modauth.domain.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
    name = "modauth_invitations",
    indexes = {
        @Index(name = "idx_invitation_token_hash", columnList = "token_hash", unique = true)
    }
)
public class InvitationEntity {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "inviter_user_id", nullable = false)
    private UUID inviterUserId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "email", nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Column(name = "team_name")
    private String teamName;

    /** Real team link when the invitee is joining an existing team (see modauth_teams); null otherwise. */
    @Column(name = "team_id")
    private UUID teamId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InvitationStatus status;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    /** Only set for TENANT_ADMIN invites created by modules/platform — see V2 migration. */
    @Column(name = "pre_allocated_user_id")
    private UUID preAllocatedUserId;

    /**
     * The plaintext token, held only in memory between {@code issueToken} and
     * the email send that immediately follows it in the same request — never
     * persisted (only {@link #tokenHash} is) and never logged.
     */
    @Transient
    private String pendingRawToken;
}
