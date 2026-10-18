package com.example.authsvc.infrastructure.security.jwt;

import com.example.authsvc.domain.TenantConstants;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlatformOnlyTenantSlugResolverTest {

    private final TenantSlugResolver resolver = new PlatformOnlyTenantSlugResolver();

    @Test
    void resolvesPlatformSentinelToPlatformSlug() {
        assertEquals("platform", resolver.resolve(TenantConstants.PLATFORM_TENANT_ID));
    }

    @Test
    void resolvesAnyOtherTenantIdToEmptyString() {
        assertEquals("", resolver.resolve(UUID.randomUUID()));
    }

    @Test
    void resolvesNamePlatformSentinelToPlatformDisplayName() {
        assertEquals("Platform", resolver.resolveName(TenantConstants.PLATFORM_TENANT_ID));
    }

    @Test
    void resolvesNameAnyOtherTenantIdToEmptyString() {
        assertEquals("", resolver.resolveName(UUID.randomUUID()));
    }
}
