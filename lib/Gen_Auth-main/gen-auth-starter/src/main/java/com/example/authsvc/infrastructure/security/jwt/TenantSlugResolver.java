package com.example.authsvc.infrastructure.security.jwt;

import java.util.UUID;

/**
 * Resolves the slug embedded in a JWT for a given tenant.
 *
 * <p>A standalone auth service has no tenant directory of its own, so the
 * default implementation ({@link PlatformOnlyTenantSlugResolver}) can only
 * resolve the platform sentinel. A host application that owns real tenant
 * data supplies its own {@code TenantSlugResolver} bean — it wins over the
 * default automatically ({@code @ConditionalOnMissingBean}) — rather than
 * this library reaching into another service's database.
 */
public interface TenantSlugResolver {

    String resolve(UUID tenantId);

    String resolveName(UUID tenantId);
}
