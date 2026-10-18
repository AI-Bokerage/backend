// gen-tnt-starter/src/main/java/com/example/tnt_svc/web/security/InternalSecretFilter.java
package com.example.tnt_svc.web.security;

import com.example.tnt_svc.config.GenTntProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Gates every /api/v1/** and /internal/** request behind a shared secret header. */
@Component
public class InternalSecretFilter extends OncePerRequestFilter {

    private final GenTntProperties properties;

    public InternalSecretFilter(GenTntProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        String path = request.getRequestURI();
        boolean gated = path.startsWith("/api/v1/") || path.startsWith("/internal/");

        if (gated) {
            String provided = request.getHeader("X-Internal-Secret");
            String expected = properties.getInternalSecret();
            if (expected == null || provided == null
                || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8))) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
        }

        chain.doFilter(request, response);
    }
}
