package io.platform.security;

import java.util.UUID;

/**
 * Platform-neutral authenticated caller. Host applications implement this over their own
 * principal model (JWT claims, session, header-based, etc.) and expose it via
 * {@code SecurityContextHolder} (or an equivalent). Reusable libraries and cross-cutting
 * infrastructure depend on this contract only — never on a specific platform's principal type.
 */
public interface AuthenticatedPrincipal {

    /** The identity of the authenticated caller. */
    UUID userId();

    /** The tenant the caller is currently scoped to. */
    UUID tenantId();

    /**
     * True only for an identity with an unconditional, cross-tenant operator capability
     * (e.g. a platform super-admin acting outside impersonation). An impersonation session
     * acting <em>as</em> a tenant must return {@code false} here — it remains bound to the
     * impersonated tenant, not exempt from tenant scoping.
     */
    boolean bypassesTenantScoping();
}
