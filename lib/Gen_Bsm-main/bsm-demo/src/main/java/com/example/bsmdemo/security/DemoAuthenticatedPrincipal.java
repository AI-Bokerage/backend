package com.example.bsmdemo.security;

import io.platform.security.AuthenticatedPrincipal;
import java.util.UUID;

/** Plain value object for a normal tenant-scoped demo caller — not a superadmin, not a Spring bean. */
public record DemoAuthenticatedPrincipal(UUID userId, UUID tenantId) implements AuthenticatedPrincipal {

    @Override
    public boolean bypassesTenantScoping() {
        return false;
    }
}
