package com.company.ppmsvc.infrastructure.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * First filter in the chain. Assigns a request ID (from {@code X-Request-Id}
 * header or generated UUID) and resolves the client IP.
 *
 * <p>Stores both in {@link RequestContext} and MDC so every log line within
 * the request carries the correlation ID automatically.
 */
@Component
@Order(Integer.MIN_VALUE)
public class PpmRequestContextFilter extends OncePerRequestFilter {

    private static final String HEADER_REQUEST_ID    = "X-Request-Id";
    private static final String HEADER_FORWARDED_FOR = "X-Forwarded-For";
    private static final String HEADER_REAL_IP       = "X-Real-IP";

    @Override
    protected void doFilterInternal(HttpServletRequest  request,
                                    HttpServletResponse response,
                                    FilterChain         chain)
            throws ServletException, IOException {

        String requestId = request.getHeader(HEADER_REQUEST_ID);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        String clientIp = extractClientIp(request);

        MDC.put(HEADER_REQUEST_ID, requestId);
        response.setHeader(HEADER_REQUEST_ID, requestId);
        RequestContext.set(new RequestContext.Info(requestId, clientIp));

        try {
            chain.doFilter(request, response);
        } finally {
            RequestContext.clear();
            MDC.remove(HEADER_REQUEST_ID);
        }
    }

    private static String extractClientIp(HttpServletRequest request) {
        String xff = request.getHeader(HEADER_FORWARDED_FOR);
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String realIp = request.getHeader(HEADER_REAL_IP);
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
