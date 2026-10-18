package com.example.modauth.config;

import com.example.authsvc.domain.TenantConstants;
import com.example.authsvc.infrastructure.security.jwt.TenantSlugResolver;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * gen-auth-starter ships a default {@code PlatformOnlyTenantSlugResolver}
 * gated by {@code @ConditionalOnMissingBean(TenantSlugResolver.class)} — in
 * practice that conditional default, being a plain {@code @Component}
 * discovered only via the starter's own {@code @ComponentScan} rather than a
 * {@code @Bean} method, did not register in this app (confirmed by an actual
 * boot failure: "No qualifying bean of type TenantSlugResolver"), so this
 * supplies the same behavior directly — exactly the extension point
 * {@code TenantSlugResolver}'s own javadoc describes ("a host application
 * ... supplies its own bean").
 *
 * <p>ponytail: platform-sentinel-only, same as the starter's own default —
 * upgrade to resolve real brokerage slugs once modauth has a brokerage/tenant
 * directory (out of scope for the 5 flow diagrams this module implements).
 */
@Component
public class ModAuthTenantSlugResolver implements TenantSlugResolver {

    @Override
    public String resolve(UUID tenantId) {
        return TenantConstants.PLATFORM_TENANT_ID.equals(tenantId) ? "platform" : "";
    }

    @Override
    public String resolveName(UUID tenantId) {
        return TenantConstants.PLATFORM_TENANT_ID.equals(tenantId) ? "Platform" : "";
    }
}
