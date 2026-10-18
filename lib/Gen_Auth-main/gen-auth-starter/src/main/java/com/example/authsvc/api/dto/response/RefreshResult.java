package com.example.authsvc.api.dto.response;

import java.time.Duration;

/**
 * Internal carrier between {@code RefreshTokenService} and {@code AuthController}.
 * Never serialized directly — tokens are set as HttpOnly cookies by the controller.
 */
public record RefreshResult(
        RefreshResponse response,
        String accessToken,
        String refreshToken,
        Duration accessTokenTtl,
        Duration refreshTokenTtl
) {}
