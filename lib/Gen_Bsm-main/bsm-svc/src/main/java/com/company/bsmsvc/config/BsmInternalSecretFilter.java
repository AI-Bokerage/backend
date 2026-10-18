package com.company.bsmsvc.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Validates the {@code X-Internal-Secret} header on all {@code /internal/**} paths.
 *
 * <p>The secret is shared between BSM-SVC and any trusted internal caller.
 * It is configured via the {@code bsm.internal-secret} property and must match
 * the {@code INTERNAL_SERVICE_SECRET} environment variable on all callers.</p>
 *
 * <p>Runs before the Spring Security JWT filter chain so that internal endpoints
 * can be permitted without a user JWT while still being protected against
 * unauthenticated external access.</p>
 *
 * <p>Fail-closed: if {@code bsm.internal-secret} is not configured the filter
 * rejects all {@code /internal/**} requests with 401 rather than opening the gate.</p>
 */
public class BsmInternalSecretFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(BsmInternalSecretFilter.class);

    static final String HEADER = "X-Internal-Secret";

    private final String expectedSecret;

    public BsmInternalSecretFilter(String expectedSecret) {
        this.expectedSecret = expectedSecret;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest  request,
                                    HttpServletResponse response,
                                    FilterChain         chain) throws ServletException, IOException {

        String provided = request.getHeader(HEADER);

        if (expectedSecret == null || expectedSecret.isBlank()) {
            log.warn("[BsmInternalSecretFilter] bsm.internal-secret not configured — rejecting /internal/** request");
            sendUnauthorized(response, "Internal service secret is not configured on this service");
            return;
        }

        if (provided == null || !provided.equals(expectedSecret)) {
            log.warn("[BsmInternalSecretFilter] Invalid or missing {} header on {}", HEADER, request.getRequestURI());
            sendUnauthorized(response, "Invalid or missing internal service secret");
            return;
        }

        chain.doFilter(request, response);
    }

    private static void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"error\":{\"code\":\"INTERNAL_AUTH_FAILED\",\"message\":\"" + message + "\"}}"
        );
    }
}
