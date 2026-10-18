package com.example.modauth.entity;

import com.example.modauth.domain.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * One row per {@code auth_users} id (no FK — same loose-coupling gen-auth-starter
 * itself uses for {@code role_id}), naming the role gen-auth-starter's own
 * {@code user_type}/{@code role_id} deliberately don't: which of the four
 * platform roles this user is, and how many terms-of-service versions they've
 * accepted.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "modauth_user_roles")
public class ModAuthUserRoleEntity {

    @Id
    @Column(name = "user_id", updatable = false, nullable = false)
    private UUID userId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "name")
    private String name;

    @Column(name = "phone", length = 50)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Column(name = "team_name")
    private String teamName;

    /** FK-by-convention to {@code modauth_teams.id} (no real FK, same loose coupling as tenantId). Null = not on a team. */
    @Column(name = "team_id")
    private UUID teamId;

    @Column(name = "accepted_terms_version", nullable = false)
    private int acceptedTermsVersion;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
