package com.company.ppmsvc.unit.security;

import com.company.ppmsvc.infrastructure.security.PpmAccessAuthorizationFilter;
import com.company.ppmsvc.infrastructure.security.RedisRolePermissionResolver;
import com.company.ppmsvc.infrastructure.security.CpmsAuthenticatedPrincipal;
import com.company.ppmsvc.infrastructure.security.CpmsUserType;
import jakarta.servlet.FilterChain;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class PpmAccessAuthorizationFilterTest {

    @Mock private RedisRolePermissionResolver rolePermissionResolver;

    @InjectMocks
    private PpmAccessAuthorizationFilter filter;

    @BeforeEach
    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void setUpPrincipal(CpmsUserType userType) {
        UUID roleId = (userType == CpmsUserType.SUPER_ADMIN) ? null : UUID.randomUUID();
        CpmsAuthenticatedPrincipal principal = new CpmsAuthenticatedPrincipal(
            UUID.randomUUID(), UUID.randomUUID(), "slug", roleId,
            userType, "session", "jti-1", Instant.now().plusSeconds(900)
        );
        var auth = new UsernamePasswordAuthenticationToken(
            principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    /** Creates a MockHttpServletRequest with both URI and servletPath set. */
    private static MockHttpServletRequest req(String method, String path) {
        MockHttpServletRequest req = new MockHttpServletRequest(method, path);
        req.setServletPath(path);
        return req;
    }

    // ── Public GET paths ──────────────────────────────────────────────────────

    @Test
    void publicGet_plansList_noAuth_passesThrough() throws Exception {
        MockHttpServletResponse resp  = new MockHttpServletResponse();
        FilterChain             chain = new MockFilterChain();

        filter.doFilter(req("GET", "/api/v1/ppm/plans"), resp, chain);

        assertThat(resp.getStatus()).isEqualTo(200);
        verify(rolePermissionResolver, never()).resolveAll(anyList());
    }

    @Test
    void publicGet_planById_noAuth_passesThrough() throws Exception {
        MockHttpServletResponse resp  = new MockHttpServletResponse();
        FilterChain             chain = new MockFilterChain();

        filter.doFilter(req("GET", "/api/v1/ppm/plans/" + UUID.randomUUID()), resp, chain);

        assertThat(resp.getStatus()).isEqualTo(200);
        verify(rolePermissionResolver, never()).resolveAll(anyList());
    }

    @Test
    void publicGet_planBySlug_noAuth_passesThrough() throws Exception {
        MockHttpServletResponse resp  = new MockHttpServletResponse();
        FilterChain             chain = new MockFilterChain();

        filter.doFilter(req("GET", "/api/v1/ppm/plans/slug/starter-monthly"), resp, chain);

        assertThat(resp.getStatus()).isEqualTo(200);
        verify(rolePermissionResolver, never()).resolveAll(anyList());
    }

    @Test
    void publicGet_planSubResource_requiresAuth() throws Exception {
        // /plans/{planId}/pricing-history is NOT on the public whitelist — sub-resources
        // are protected by default unless explicitly listed, no principal → 401
        MockHttpServletResponse resp  = new MockHttpServletResponse();
        FilterChain             chain = new MockFilterChain();

        filter.doFilter(req("GET", "/api/v1/ppm/plans/" + UUID.randomUUID() + "/pricing-history"), resp, chain);

        assertThat(resp.getStatus()).isEqualTo(401);
    }

    @Test
    void publicGet_planModules_noAuth_passesThrough() throws Exception {
        // /plans/{planId}/modules IS on the public whitelist — REG-SVC's signup
        // pricing page reads it before the visitor has a session.
        MockHttpServletResponse resp  = new MockHttpServletResponse();
        FilterChain             chain = new MockFilterChain();

        filter.doFilter(req("GET", "/api/v1/ppm/plans/" + UUID.randomUUID() + "/modules"), resp, chain);

        assertThat(resp.getStatus()).isEqualTo(200);
        verify(rolePermissionResolver, never()).resolveAll(anyList());
    }

    // ── Public POST paths ─────────────────────────────────────────────────────

    @Test
    void publicPost_pricingResolve_noAuth_passesThrough() throws Exception {
        MockHttpServletResponse resp  = new MockHttpServletResponse();
        FilterChain             chain = new MockFilterChain();

        filter.doFilter(req("POST", "/api/v1/ppm/prices/resolve"), resp, chain);

        assertThat(resp.getStatus()).isEqualTo(200);
        verify(rolePermissionResolver, never()).resolveAll(anyList());
    }

    @Test
    void publicPost_promoValidate_noAuth_passesThrough() throws Exception {
        MockHttpServletResponse resp  = new MockHttpServletResponse();
        FilterChain             chain = new MockFilterChain();

        filter.doFilter(req("POST", "/api/v1/ppm/promo-codes/validate"), resp, chain);

        assertThat(resp.getStatus()).isEqualTo(200);
        verify(rolePermissionResolver, never()).resolveAll(anyList());
    }

    // ── No principal ──────────────────────────────────────────────────────────

    @Test
    void noPrincipal_nonPublicPath_returns401() throws Exception {
        MockHttpServletResponse resp  = new MockHttpServletResponse();
        FilterChain             chain = new MockFilterChain();

        filter.doFilter(req("GET", "/api/v1/ppm/modules"), resp, chain);

        assertThat(resp.getStatus()).isEqualTo(401);
    }

    // ── SUPER_ADMIN bypass ────────────────────────────────────────────────────

    @Test
    void superAdmin_nonPublicPath_bypassesRedis() throws Exception {
        setUpPrincipal(CpmsUserType.SUPER_ADMIN);
        MockHttpServletResponse resp  = new MockHttpServletResponse();
        FilterChain             chain = new MockFilterChain();

        filter.doFilter(req("GET", "/api/v1/ppm/modules"), resp, chain);

        assertThat(resp.getStatus()).isEqualTo(200);
        verify(rolePermissionResolver, never()).resolveAll(anyList());
    }

    @Test
    void superAdminImpersonating_nonPublicPath_bypassesRedis() throws Exception {
        setUpPrincipal(CpmsUserType.SUPER_ADMIN_IMPERSONATING);
        MockHttpServletResponse resp  = new MockHttpServletResponse();
        FilterChain             chain = new MockFilterChain();

        filter.doFilter(req("GET", "/api/v1/ppm/promo-codes"), resp, chain);

        assertThat(resp.getStatus()).isEqualTo(200);
        verify(rolePermissionResolver, never()).resolveAll(anyList());
    }

    // ── Tenant user permission checks ─────────────────────────────────────────

    @Test
    void tenantUser_withPpmRead_passesThrough() throws Exception {
        setUpPrincipal(CpmsUserType.TENANT_USER);
        when(rolePermissionResolver.resolveAll(anyList())).thenReturn(Set.of("ppm.read", "ppm.access"));
        MockHttpServletResponse resp  = new MockHttpServletResponse();
        FilterChain             chain = new MockFilterChain();

        filter.doFilter(req("GET", "/api/v1/ppm/modules"), resp, chain);

        assertThat(resp.getStatus()).isEqualTo(200);
    }

    @Test
    void tenantUser_withoutPpmRead_returns403() throws Exception {
        setUpPrincipal(CpmsUserType.TENANT_USER);
        when(rolePermissionResolver.resolveAll(anyList())).thenReturn(Set.of("ppm.access"));
        MockHttpServletResponse resp  = new MockHttpServletResponse();
        FilterChain             chain = new MockFilterChain();

        filter.doFilter(req("GET", "/api/v1/ppm/modules"), resp, chain);

        assertThat(resp.getStatus()).isEqualTo(403);
        assertThat(resp.getContentAsString()).contains("ppm.read");
    }

    @Test
    void tenantUser_emptyPermissions_returns403() throws Exception {
        setUpPrincipal(CpmsUserType.TENANT_USER);
        when(rolePermissionResolver.resolveAll(anyList())).thenReturn(Set.of());
        MockHttpServletResponse resp  = new MockHttpServletResponse();
        FilterChain             chain = new MockFilterChain();

        filter.doFilter(req("GET", "/api/v1/ppm/entitlements"), resp, chain);

        assertThat(resp.getStatus()).isEqualTo(403);
    }
}
