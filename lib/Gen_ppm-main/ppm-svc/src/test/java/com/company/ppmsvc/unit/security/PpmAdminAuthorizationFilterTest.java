package com.company.ppmsvc.unit.security;

import com.company.ppmsvc.infrastructure.security.PpmAdminAuthorizationFilter;
import com.company.ppmsvc.infrastructure.security.CpmsAuthenticatedPrincipal;
import com.company.ppmsvc.infrastructure.security.CpmsUserType;
import com.company.ppmsvc.infrastructure.security.PermitAllPpmAuthorizationService;
import com.company.ppmsvc.infrastructure.security.PlatformPpmAuthorizationService;
import jakarta.servlet.FilterChain;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class PpmAdminAuthorizationFilterTest {

    private final PpmAdminAuthorizationFilter filter =
        new PpmAdminAuthorizationFilter(new PlatformPpmAuthorizationService());

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

    private static MockHttpServletRequest req(String method, String path) {
        MockHttpServletRequest req = new MockHttpServletRequest(method, path);
        req.setServletPath(path);
        return req;
    }

    // ── GET requests are pass-through (excluded by shouldNotFilter) ───────────

    @Test
    void getRequest_passesThrough_noAuth() throws Exception {
        FilterChain             chain = new MockFilterChain();
        MockHttpServletResponse resp  = new MockHttpServletResponse();

        filter.doFilter(req("GET", "/api/v1/ppm/modules"), resp, chain);

        assertThat(resp.getStatus()).isEqualTo(200);
    }

    @Test
    void actuatorPost_passesThrough() throws Exception {
        FilterChain             chain = new MockFilterChain();
        MockHttpServletResponse resp  = new MockHttpServletResponse();

        filter.doFilter(req("POST", "/actuator/health"), resp, chain);

        assertThat(resp.getStatus()).isEqualTo(200);
    }

    // ── Public POST paths: exempt from write gate ─────────────────────────────

    @Test
    void publicPost_pricingResolve_noAuth_passesThrough() throws Exception {
        FilterChain             chain = new MockFilterChain();
        MockHttpServletResponse resp  = new MockHttpServletResponse();

        filter.doFilter(req("POST", "/api/v1/ppm/prices/resolve"), resp, chain);

        assertThat(resp.getStatus()).isEqualTo(200);
    }

    @Test
    void publicPost_promoValidate_noAuth_passesThrough() throws Exception {
        FilterChain             chain = new MockFilterChain();
        MockHttpServletResponse resp  = new MockHttpServletResponse();

        filter.doFilter(req("POST", "/api/v1/ppm/promo-codes/validate"), resp, chain);

        assertThat(resp.getStatus()).isEqualTo(200);
    }

    // ── SUPER_ADMIN write access ──────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
    void superAdmin_writeMethods_passesThrough(String method) throws Exception {
        setUpPrincipal(CpmsUserType.SUPER_ADMIN);
        FilterChain             chain = new MockFilterChain();
        MockHttpServletResponse resp  = new MockHttpServletResponse();

        filter.doFilter(req(method, "/api/v1/ppm/plans"), resp, chain);

        assertThat(resp.getStatus()).isEqualTo(200);
    }

    @Test
    void superAdminImpersonating_post_passesThrough() throws Exception {
        setUpPrincipal(CpmsUserType.SUPER_ADMIN_IMPERSONATING);
        FilterChain             chain = new MockFilterChain();
        MockHttpServletResponse resp  = new MockHttpServletResponse();

        filter.doFilter(req("POST", "/api/v1/ppm/modules"), resp, chain);

        assertThat(resp.getStatus()).isEqualTo(200);
    }

    // ── Tenant user write attempts: all 4 methods blocked ────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
    void tenantUser_writeMethods_returns403(String method) throws Exception {
        setUpPrincipal(CpmsUserType.TENANT_USER);
        FilterChain             chain = new MockFilterChain();
        MockHttpServletResponse resp  = new MockHttpServletResponse();

        filter.doFilter(req(method, "/api/v1/ppm/plans"), resp, chain);

        assertThat(resp.getStatus()).isEqualTo(403);
        assertThat(resp.getContentAsString()).contains("Super Admin");
    }

    @Test
    void tenantUser_deleteEntitlement_returns403() throws Exception {
        setUpPrincipal(CpmsUserType.TENANT_USER);
        FilterChain             chain = new MockFilterChain();
        MockHttpServletResponse resp  = new MockHttpServletResponse();

        filter.doFilter(req("DELETE", "/api/v1/ppm/entitlements/" + UUID.randomUUID()), resp, chain);

        assertThat(resp.getStatus()).isEqualTo(403);
    }

    @Test
    void tenantUser_replaceModules_returns403() throws Exception {
        setUpPrincipal(CpmsUserType.TENANT_USER);
        FilterChain             chain = new MockFilterChain();
        MockHttpServletResponse resp  = new MockHttpServletResponse();

        filter.doFilter(req("PUT", "/api/v1/ppm/plans/" + UUID.randomUUID() + "/modules"), resp, chain);

        assertThat(resp.getStatus()).isEqualTo(403);
    }

    // ── No principal on write path ────────────────────────────────────────────

    @Test
    void noPrincipal_writePath_returns401() throws Exception {
        FilterChain             chain = new MockFilterChain();
        MockHttpServletResponse resp  = new MockHttpServletResponse();

        filter.doFilter(req("POST", "/api/v1/ppm/plans"), resp, chain);

        assertThat(resp.getStatus()).isEqualTo(401);
    }

    // ── Quotes: exempt from this filter, authorized via its own SPI hook ─────

    @Test
    void quotesPost_noAuth_passesThrough() throws Exception {
        FilterChain             chain = new MockFilterChain();
        MockHttpServletResponse resp  = new MockHttpServletResponse();

        filter.doFilter(req("POST", "/api/v1/ppm/quotes"), resp, chain);

        assertThat(resp.getStatus()).isEqualTo(200);
    }

    // ── Delegation: the filter defers entirely to PpmAuthorizationService ────

    @Test
    void delegatesToAuthorizationService_notHardcodedRole() throws Exception {
        setUpPrincipal(CpmsUserType.TENANT_USER);
        PpmAdminAuthorizationFilter permissiveFilter =
            new PpmAdminAuthorizationFilter(new PermitAllPpmAuthorizationService());
        FilterChain             chain = new MockFilterChain();
        MockHttpServletResponse resp  = new MockHttpServletResponse();

        permissiveFilter.doFilter(req("POST", "/api/v1/ppm/plans"), resp, chain);

        assertThat(resp.getStatus()).isEqualTo(200);
    }
}
