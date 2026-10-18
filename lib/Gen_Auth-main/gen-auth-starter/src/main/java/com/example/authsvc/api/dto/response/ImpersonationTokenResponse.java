package com.example.authsvc.api.dto.response;

/**
 * Response from {@code POST /internal/auth/impersonation-token}.
 *
 * @param accessToken the signed RS256 JWT with {@code user_type=SUPER_ADMIN_IMPERSONATING}
 * @param expiresIn   token lifetime in seconds
 * @param sessionId   the caller-supplied correlation ID, echoed back
 */
public record ImpersonationTokenResponse(
        String accessToken,
        int    expiresIn,
        String sessionId
) {}
