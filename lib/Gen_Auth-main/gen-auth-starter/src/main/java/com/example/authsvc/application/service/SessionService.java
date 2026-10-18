package com.example.authsvc.application.service;

import com.example.authsvc.api.dto.response.SessionResponse;
import com.example.authsvc.infrastructure.security.principal.AuthenticatedUser;
import jakarta.servlet.http.HttpServletResponse;

public interface SessionService {

    /**
     * Returns the current session payload for the authenticated user.
     *
     * <p>Validates the session is still active in the database. If the session
     * is invalid (revoked, expired, or not found) the auth cookies are cleared
     * and {@link com.example.authsvc.common.exception.UnauthorizedException} is
     * thrown.
     *
     * @param principal the authenticated principal extracted by the JWT filter
     * @param response  HTTP response used to clear cookies on invalid session
     */
    SessionResponse getSession(AuthenticatedUser principal, HttpServletResponse response);
}
