package com.company.bsmsvc.infrastructure.security;

import io.cpms.common.security.CpmsAuthenticatedPrincipal;
import io.cpms.common.security.CpmsUserType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BsmTenantScopeEnforcerTest {

    private final BsmTenantScopeEnforcer enforcer = new BsmTenantScopeEnforcer();

    private static final UUID TENANT_A = UUID.randomUUID();
    private static final UUID TENANT_B = UUID.randomUUID();
    private static final UUID USER_ID  = UUID.randomUUID();
    private static final UUID ROLE_ID  = UUID.randomUUID();

    @BeforeEach
    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void setContext(UUID userId, UUID tenantId, CpmsUserType userType) {
        CpmsAuthenticatedPrincipal principal = new CpmsAuthenticatedPrincipal(
            userId, tenantId, "test-slug", ROLE_ID, userType, null, null, Instant.MAX
        );
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of())
        );
    }

    // ── assertTenantAccess ────────────────────────────────────────────────

    @Test
    void assertTenantAccess_matchingTenant_tenantUser_succeeds() {
        setContext(USER_ID, TENANT_A, CpmsUserType.TENANT_USER);
        assertThatNoException().isThrownBy(() -> enforcer.assertTenantAccess(TENANT_A));
    }

    @Test
    void assertTenantAccess_mismatchedTenant_tenantUser_throws403() {
        setContext(USER_ID, TENANT_A, CpmsUserType.TENANT_USER);
        assertThatThrownBy(() -> enforcer.assertTenantAccess(TENANT_B))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining(TENANT_B.toString());
    }

    @Test
    void assertTenantAccess_superAdmin_bypassesForAnyTenant() {
        setContext(USER_ID, null, CpmsUserType.SUPER_ADMIN);
        assertThatNoException().isThrownBy(() -> enforcer.assertTenantAccess(TENANT_A));
        assertThatNoException().isThrownBy(() -> enforcer.assertTenantAccess(TENANT_B));
    }

    @Test
    void assertTenantAccess_impersonating_matchingTenant_succeeds() {
        setContext(USER_ID, TENANT_A, CpmsUserType.SUPER_ADMIN_IMPERSONATING);
        assertThatNoException().isThrownBy(() -> enforcer.assertTenantAccess(TENANT_A));
    }

    @Test
    void assertTenantAccess_impersonating_mismatchedTenant_throws() {
        // SUPER_ADMIN_IMPERSONATING is bound to the impersonated tenant in the JWT
        setContext(USER_ID, TENANT_A, CpmsUserType.SUPER_ADMIN_IMPERSONATING);
        assertThatThrownBy(() -> enforcer.assertTenantAccess(TENANT_B))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void assertTenantAccess_clientUser_matchingTenant_succeeds() {
        setContext(USER_ID, TENANT_A, CpmsUserType.CLIENT);
        assertThatNoException().isThrownBy(() -> enforcer.assertTenantAccess(TENANT_A));
    }

    @Test
    void assertTenantAccess_clientUser_mismatchedTenant_throws() {
        setContext(USER_ID, TENANT_A, CpmsUserType.CLIENT);
        assertThatThrownBy(() -> enforcer.assertTenantAccess(TENANT_B))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void assertTenantAccess_nullTenantId_throws() {
        setContext(USER_ID, TENANT_A, CpmsUserType.TENANT_USER);
        assertThatThrownBy(() -> enforcer.assertTenantAccess(null))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void assertTenantAccess_nullTenantId_superAdmin_stillThrows() {
        // null tenantId is always an error regardless of user type — it means misconfigured caller
        setContext(USER_ID, null, CpmsUserType.SUPER_ADMIN);
        assertThatThrownBy(() -> enforcer.assertTenantAccess(null))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void assertTenantAccess_noSecurityContext_allowsThrough() {
        // No authentication = system/scheduler context — trusted internal caller, must not be blocked
        assertThatNoException().isThrownBy(() -> enforcer.assertTenantAccess(TENANT_A));
    }

    // ── resolveEffectiveTenantId ──────────────────────────────────────────

    @Test
    void resolveEffectiveTenantId_tenantUser_returnsJwtTenantRegardlessOfRequest() {
        setContext(USER_ID, TENANT_A, CpmsUserType.TENANT_USER);
        // Caller passes TENANT_B — should be overridden by JWT
        assertThat(enforcer.resolveEffectiveTenantId(TENANT_B)).isEqualTo(TENANT_A);
    }

    @Test
    void resolveEffectiveTenantId_tenantUser_nullRequest_returnsJwtTenant() {
        setContext(USER_ID, TENANT_A, CpmsUserType.TENANT_USER);
        // Caller provides no tenantId — still scoped to their JWT tenant
        assertThat(enforcer.resolveEffectiveTenantId(null)).isEqualTo(TENANT_A);
    }

    @Test
    void resolveEffectiveTenantId_superAdmin_specificTenant_passesThrough() {
        setContext(USER_ID, null, CpmsUserType.SUPER_ADMIN);
        assertThat(enforcer.resolveEffectiveTenantId(TENANT_A)).isEqualTo(TENANT_A);
    }

    @Test
    void resolveEffectiveTenantId_superAdmin_null_returnsNullAllowingCrossTenatQuery() {
        setContext(USER_ID, null, CpmsUserType.SUPER_ADMIN);
        // SUPER_ADMIN querying all tenants — null passes through
        assertThat(enforcer.resolveEffectiveTenantId(null)).isNull();
    }

    @Test
    void resolveEffectiveTenantId_impersonating_returnsJwtTenantNotRequestedTenant() {
        setContext(USER_ID, TENANT_A, CpmsUserType.SUPER_ADMIN_IMPERSONATING);
        // Impersonating admin is bound to impersonated tenant in JWT
        assertThat(enforcer.resolveEffectiveTenantId(TENANT_B)).isEqualTo(TENANT_A);
    }

    // ── getPrincipal ──────────────────────────────────────────────────────

    @Test
    void getPrincipal_authenticated_returnsPrincipal() {
        setContext(USER_ID, TENANT_A, CpmsUserType.TENANT_USER);
        io.platform.security.AuthenticatedPrincipal principal = enforcer.getPrincipal();
        assertThat(principal.userId()).isEqualTo(USER_ID);
        assertThat(principal.tenantId()).isEqualTo(TENANT_A);
    }

    @Test
    void getPrincipal_notAuthenticated_returnsNull() {
        // System/scheduler context — getPrincipal returns null rather than throwing
        assertThat(enforcer.getPrincipal()).isNull();
    }
}
