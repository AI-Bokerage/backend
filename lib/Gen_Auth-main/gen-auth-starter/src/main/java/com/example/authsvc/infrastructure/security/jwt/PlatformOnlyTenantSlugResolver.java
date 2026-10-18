package com.example.authsvc.infrastructure.security.jwt;

import com.example.authsvc.domain.TenantConstants;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Default {@link TenantSlugResolver}: resolves only the platform sentinel,
 * every other tenant id resolves to an empty string. Backed off by any
 * host-supplied {@code TenantSlugResolver} bean.
 */
@Component
@ConditionalOnMissingBean(TenantSlugResolver.class)
public class PlatformOnlyTenantSlugResolver implements TenantSlugResolver {

    @Override
    public String resolve(UUID tenantId) {
        return TenantConstants.PLATFORM_TENANT_ID.equals(tenantId) ? "platform" : "";
    }

    @Override
    public String resolveName(UUID tenantId) {
        return TenantConstants.PLATFORM_TENANT_ID.equals(tenantId) ? "Platform" : "";
    }
}
