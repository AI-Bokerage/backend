package com.example.authsvc.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Response body returned after a successful token refresh")
public record RefreshResponse(
        @Schema(description = "UTC timestamp when the new access token expires")
        Instant accessTokenExpiresAt,

        @Schema(description = "JWT access token — only populated when auth.token-delivery-mode=json")
        String accessToken,

        @Schema(description = "Raw refresh token — only populated when auth.token-delivery-mode=json")
        String refreshToken
) {
    /** Backward-compat constructor for the existing cookie-mode call site — tokens travel via Set-Cookie, not this DTO. */
    public RefreshResponse(Instant accessTokenExpiresAt) {
        this(accessTokenExpiresAt, null, null);
    }
}
