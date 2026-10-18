package com.example.gendemo;

import com.example.admsvc.domain.port.GenAdmPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * FOR LOCAL DEMO/SMOKE-TESTING ONLY — reads tenant/user IDs from plain
 * headers with no real authentication. A real host application wires its
 * own Gen_AUTH-issued-JWT-backed {@code GenAdmPrincipal} here instead.
 */
@Component
public class DemoPrincipalAuthFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String tenantHeader = request.getHeader("X-Tenant-Id");
        String userHeader = request.getHeader("X-User-Id");
        if (tenantHeader != null && userHeader != null) {
            GenAdmPrincipal principal = new GenAdmPrincipal() {
                @Override
                public UUID tenantId() {
                    return UUID.fromString(tenantHeader);
                }

                @Override
                public UUID userId() {
                    return UUID.fromString(userHeader);
                }
            };
            SecurityContextHolder.getContext().setAuthentication(
                    new TestingAuthenticationToken(principal, null));
        }
        chain.doFilter(request, response);
    }
}
