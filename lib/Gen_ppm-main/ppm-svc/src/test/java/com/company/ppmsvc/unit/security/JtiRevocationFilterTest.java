package com.company.ppmsvc.unit.security;

import com.company.ppmsvc.infrastructure.security.JtiRevocationFilter;
import com.company.ppmsvc.infrastructure.security.CpmsAuthenticatedPrincipal;
import com.company.ppmsvc.infrastructure.security.CpmsUserType;
import jakarta.servlet.FilterChain;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class JtiRevocationFilterTest {

    @Mock private StringRedisTemplate redisTemplate;

    @InjectMocks
    private JtiRevocationFilter filter;

    @BeforeEach
    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void setUpPrincipal(String jti, CpmsUserType userType) {
        UUID roleId = (userType == CpmsUserType.SUPER_ADMIN) ? null : UUID.randomUUID();
        CpmsAuthenticatedPrincipal principal = new CpmsAuthenticatedPrincipal(
            UUID.randomUUID(), UUID.randomUUID(), "slug", roleId,
            userType, "session-1", jti, Instant.now().plusSeconds(900)
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

    @Test
    void noPrincipal_passesThrough() throws Exception {
        MockHttpServletResponse resp  = new MockHttpServletResponse();
        FilterChain             chain = new MockFilterChain();

        filter.doFilter(req("GET", "/api/v1/ppm/plans"), resp, chain);

        assertThat(resp.getStatus()).isEqualTo(200);
        verify(redisTemplate, never()).hasKey(anyString());
    }

    @Test
    void principalWithNullJti_passesThrough() throws Exception {
        setUpPrincipal(null, CpmsUserType.TENANT_USER);
        MockHttpServletResponse resp  = new MockHttpServletResponse();
        FilterChain             chain = new MockFilterChain();

        filter.doFilter(req("GET", "/api/v1/ppm/modules"), resp, chain);

        assertThat(resp.getStatus()).isEqualTo(200);
        verify(redisTemplate, never()).hasKey(anyString());
    }

    @Test
    void validJti_notRevoked_passesThrough() throws Exception {
        setUpPrincipal("jti-abc-123", CpmsUserType.TENANT_USER);
        when(redisTemplate.hasKey("auth:revoked:jti-abc-123")).thenReturn(false);
        MockHttpServletResponse resp  = new MockHttpServletResponse();
        FilterChain             chain = new MockFilterChain();

        filter.doFilter(req("GET", "/api/v1/ppm/modules"), resp, chain);

        assertThat(resp.getStatus()).isEqualTo(200);
    }

    @Test
    void revokedJti_returns401() throws Exception {
        setUpPrincipal("jti-revoked-999", CpmsUserType.TENANT_USER);
        when(redisTemplate.hasKey("auth:revoked:jti-revoked-999")).thenReturn(true);
        MockHttpServletResponse resp  = new MockHttpServletResponse();
        FilterChain             chain = new MockFilterChain();

        filter.doFilter(req("GET", "/api/v1/ppm/modules"), resp, chain);

        assertThat(resp.getStatus()).isEqualTo(401);
        assertThat(resp.getContentAsString()).contains("revoked");
    }

    @Test
    void redisUnavailable_failOpen_passesThrough() throws Exception {
        setUpPrincipal("jti-xyz", CpmsUserType.TENANT_USER);
        when(redisTemplate.hasKey(anyString())).thenThrow(new RuntimeException("Redis down"));
        MockHttpServletResponse resp  = new MockHttpServletResponse();
        FilterChain             chain = new MockFilterChain();

        filter.doFilter(req("GET", "/api/v1/ppm/modules"), resp, chain);

        assertThat(resp.getStatus()).isEqualTo(200);
    }

    @Test
    void superAdminRevokedJti_returns401() throws Exception {
        setUpPrincipal("jti-sa-revoked", CpmsUserType.SUPER_ADMIN);
        when(redisTemplate.hasKey("auth:revoked:jti-sa-revoked")).thenReturn(true);
        MockHttpServletResponse resp  = new MockHttpServletResponse();
        FilterChain             chain = new MockFilterChain();

        filter.doFilter(req("POST", "/api/v1/ppm/plans"), resp, chain);

        assertThat(resp.getStatus()).isEqualTo(401);
    }

    @Test
    void actuatorPath_noRedisCall() throws Exception {
        setUpPrincipal("jti-any", CpmsUserType.TENANT_USER);
        MockHttpServletResponse resp  = new MockHttpServletResponse();
        FilterChain             chain = new MockFilterChain();

        // Actuator path hits shouldNotFilter=true, doFilter bypasses doFilterInternal
        filter.doFilter(req("GET", "/actuator/health"), resp, chain);

        verify(redisTemplate, never()).hasKey(anyString());
    }
}
