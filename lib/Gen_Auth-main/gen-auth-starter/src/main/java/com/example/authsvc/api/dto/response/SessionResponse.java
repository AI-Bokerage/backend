package com.example.authsvc.api.dto.response;

import java.time.Instant;
import java.util.Set;

/**
 * Response body for {@code GET /api/v1/auth/session}.
 *
 * <p>Used by frontends to bootstrap session state, initialize permissions,
 * and determine when the access token expires.
 *
 * @param user        Lightweight user projection (no secrets).
 * @param permissions Resolved permission codes for the user's role.
 * @param expiresAt   Access-token expiry instant (from JWT {@code exp} claim).
 */
public record SessionResponse(
        SessionUserResponse user,
        Set<String>         permissions,
        Instant             expiresAt
) {}
