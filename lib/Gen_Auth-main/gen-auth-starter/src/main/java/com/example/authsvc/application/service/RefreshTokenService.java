package com.example.authsvc.application.service;

import com.example.authsvc.api.dto.response.RefreshResult;

public interface RefreshTokenService {

    /**
     * Validates the raw refresh token, rotates the token pair, and returns
     * new tokens. Throws {@link com.example.authsvc.common.exception.UnauthorizedException}
     * for any invalid state. On replay detection, revokes the session first.
     *
     * @param rawRefreshToken plaintext token from the HttpOnly cookie
     * @param ipAddress       caller IP for audit logging
     * @param userAgent       caller User-Agent for audit logging
     */
    RefreshResult refresh(String rawRefreshToken, String ipAddress, String userAgent);

    /**
     * Explicit logout: revokes the refresh token family, deactivates the session,
     * publishes the {@code auth.logout} event, and writes an audit record.
     * Idempotent — safe to call with a null/blank/already-expired token.
     *
     * @param rawRefreshToken plaintext token from the HttpOnly cookie (may be null)
     * @param ipAddress       caller IP for audit logging
     * @param userAgent       caller User-Agent for audit logging
     */
    void logout(String rawRefreshToken, String ipAddress, String userAgent);
}
