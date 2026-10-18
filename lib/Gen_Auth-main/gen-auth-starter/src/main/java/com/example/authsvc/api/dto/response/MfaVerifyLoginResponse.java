package com.example.authsvc.api.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response for {@code POST /mfa/verify-login} when the call also completed
 * a first-time enrollment (tenant-required-but-previously-unenrolled user) —
 * carries the same fields as {@link LoginResponse} plus the 10 one-time-visible
 * backup codes, since this is the only login-completing path where backup
 * codes are ever generated but no separate {@code /mfa/enroll/confirm} call
 * happens to surface them.
 */
public record MfaVerifyLoginResponse(
        UUID userId,
        String email,
        Instant accessTokenExpiresAt,
        String accessToken,
        String refreshToken,
        List<String> backupCodes
) {}
