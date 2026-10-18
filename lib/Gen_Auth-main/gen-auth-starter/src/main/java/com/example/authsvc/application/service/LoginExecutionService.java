package com.example.authsvc.application.service;

import com.example.authsvc.api.dto.request.LoginRequest;
import com.example.authsvc.api.dto.response.LoginResult;
import com.example.authsvc.infrastructure.persistence.entity.AuthUserEntity;

import java.util.UUID;

/**
 * Executes the core login critical path for a pre-resolved, pre-authenticated user.
 *
 * <p>{@link LoginService} looks up the user record, then delegates here to share a
 * single implementation of password verification, session persistence, JWT
 * generation, Redis token storage, and async side-effects.
 */
public interface LoginExecutionService {

    /**
     * Runs the full login critical path starting from password verification.
     *
     * @param user       pre-resolved and active user entity (from {@code auth_users}
     *                   or a synthetic entity built from {@code platform_super_admin})
     * @param request    original login request (credentials + metadata)
     * @param ipAddress  client IP address
     * @param userAgent  client User-Agent header value
     * @param loginStart {@code System.currentTimeMillis()} captured at the entry of
     *                   the login handler, used for end-to-end latency logging
     * @return a {@link LoginResult} carrying the response body and token strings
     */
    LoginResult executeLogin(AuthUserEntity user, LoginRequest request,
                             String ipAddress, String userAgent, long loginStart);

    /**
     * Runs the login critical path from role-loading onward (session
     * persistence, JWT generation, Redis token storage, async side-effects),
     * skipping password verification and any MFA decision. Used by
     * {@link #executeLogin} once password verification succeeds and no MFA
     * challenge is required.
     *
     * @param user       pre-resolved, password-verified, active user entity
     * @param request    original login request (credentials + metadata)
     * @param ipAddress  client IP address
     * @param userAgent  client User-Agent header value
     * @param loginStart {@code System.currentTimeMillis()} captured at the entry of
     *                   the login handler, used for end-to-end latency logging
     * @return a {@link LoginResult} carrying the response body and token strings
     */
    LoginResult issueTokens(AuthUserEntity user, LoginRequest request,
                            String ipAddress, String userAgent, long loginStart);

    /**
     * Runs the login critical path from the MFA decision onward (session
     * persistence, JWT generation, Redis token storage, async side-effects),
     * for a caller that has already authenticated the user by some means
     * other than password verification (OAuth login, or completing the
     * OAuth password-setup gate). Applies the exact same {@code MfaLoginGate}
     * check {@link #executeLogin} runs after password verification succeeds.
     *
     * @param user       pre-resolved, already-authenticated, active user entity
     * @param ipAddress  client IP address
     * @param userAgent  client User-Agent header value
     * @param loginStart {@code System.currentTimeMillis()} captured at the entry of
     *                   the calling handler, used for end-to-end latency logging
     * @return a {@link LoginResult} carrying the response body and token strings,
     *         or an MFA challenge if one is required
     */
    LoginResult proceedPostAuthentication(AuthUserEntity user, String ipAddress, String userAgent, long loginStart);

    /**
     * Records a failed login attempt and dispatches async audit / event side-effects.
     *
     * @param tenantId  tenant of the looked-up user (may be {@code null} if user was not found)
     * @param userId    id of the looked-up user (may be {@code null} if user was not found)
     * @param email     the email address from the login request
     * @param ip        client IP address
     * @param userAgent client User-Agent header value
     * @param reason    machine-readable failure reason code (e.g. {@code "INVALID_CREDENTIALS"})
     */
    void handleFailure(UUID tenantId, UUID userId, String email,
                       String ip, String userAgent, String reason);
}
