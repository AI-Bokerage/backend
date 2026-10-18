package com.example.authsvc.domain.model;

import com.example.authsvc.domain.enums.UserType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Verified JWT payload. Permission resolution uses Redis at runtime via role_ids —
 * no permissions are carried in the token.
 *
 * Claim → JWT field mapping:
 *   userId     → sub
 *   tenantId   → tenant_id
 *   tenantSlug → tenant_slug
 *   roleIds    → role_ids[]   (primary, Phase 4+)
 *   roleId()   → role_id      (backward-compat scalar, derived from roleIds[0])
 *   userType   → user_type
 *   sessionId  → session_id
 *   jti        → jti
 *   issuedAt   → iat
 *   expiresAt  → exp
 *   username   → username     (nullable — display name of the acting user)
 *   userEmail  → user_email   (nullable)
 *   tenantName → tenant_name  (nullable — human-readable tenant display name)
 */
public record JwtClaims(
        UUID        userId,
        UUID        tenantId,
        String      tenantSlug,
        List<UUID>  roleIds,
        UserType    userType,
        Instant     issuedAt,
        Instant     expiresAt,
        String      sessionId,
        String      jti,
        String      username,
        String      userEmail,
        String      tenantName
) {
    /**
     * Backward-compatibility accessor. Returns the first role UUID, or {@code null}
     * when the user has no role assignments. Callers that only need a single role
     * (e.g. impersonation, session snapshot) may use this instead of iterating
     * {@link #roleIds()}.
     */
    public UUID roleId() {
        return (roleIds == null || roleIds.isEmpty()) ? null : roleIds.get(0);
    }

    public boolean isExpired(Instant now) { return expiresAt.isBefore(now); }
    public boolean isValid(Instant now)   { return !isExpired(now); }
}
