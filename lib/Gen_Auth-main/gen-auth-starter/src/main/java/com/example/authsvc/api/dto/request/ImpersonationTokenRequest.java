package com.example.authsvc.api.dto.request;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request body for {@code POST /internal/auth/impersonation-token}.
 *
 * <p>The calling internal service is expected to have already resolved
 * {@code impersonationRoleId} (and, where available, {@code tenantSlug})
 * through its own authorization flow before calling this endpoint — this
 * service is a pure token factory: it receives pre-validated data, records
 * a session, and issues a JWT.
 */
public record ImpersonationTokenRequest(

        /** UUID of the super admin initiating the impersonation. Becomes JWT {@code sub}. */
        @NotNull UUID superAdminId,

        /** UUID of the impersonated tenant. Becomes JWT {@code tenant_id}. */
        @NotNull UUID tenantId,

        /**
         * Slug of the impersonated tenant, if the caller already has it; may be
         * blank, in which case {@link com.example.authsvc.infrastructure.security.jwt.TenantSlugResolver}
         * resolves it instead.
         */
        @Nullable String tenantSlug,

        /**
         * UUID of the impersonation role for the tenant, resolved by the caller.
         * Becomes JWT {@code role_id}.
         */
        @NotNull UUID impersonationRoleId,

        /**
         * Caller-supplied correlation ID. Stored on the auth session and
         * embedded as JWT {@code session_id}.
         */
        @NotBlank String sessionId,

        /**
         * Whether write access was requested and consented to. Currently stored
         * for audit and to extend the token TTL; the impersonation role itself
         * enforces read-only permissions regardless of this value.
         */
        boolean writeConsent
) {}
