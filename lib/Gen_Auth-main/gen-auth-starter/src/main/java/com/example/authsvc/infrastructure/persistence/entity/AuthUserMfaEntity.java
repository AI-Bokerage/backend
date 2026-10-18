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
 * JPA entity for the {@code auth_user_mfa} table — one row per user who has
 * ever enrolled (or attempted to enroll) in TOTP MFA. {@code userId} is both
 * the primary key and the FK-by-convention to {@code auth_users.id} (no
 * physical FK constraint, matching this codebase's existing cross-table
 * conventions).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "auth_user_mfa")
public class AuthUserMfaEntity {

    @Id
    @Column(name = "user_id", updatable = false, nullable = false)
    private UUID userId;

    @Column(name = "totp_secret_ciphertext", nullable = false)
    private byte[] totpSecretCiphertext;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "enrolled_at")
    private Instant enrolledAt;

    @Column(name = "disabled_at")
    private Instant disabledAt;
}
