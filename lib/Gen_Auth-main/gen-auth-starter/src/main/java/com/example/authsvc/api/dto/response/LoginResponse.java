package com.example.authsvc.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Schema(description = "Successful login response body")
public class LoginResponse {

    @Schema(description = "UUID of the authenticated user", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID userId;

    @Schema(description = "Email of the authenticated user", example = "user@cpms.com")
    private String email;

    @Schema(description = "UTC timestamp when the access token expires")
    private Instant accessTokenExpiresAt;

    @Schema(description = "JWT access token. Paste this value (without 'Bearer ') into Swagger's Authorize dialog "
            + "or use as 'Authorization: Bearer <token>' in Postman/curl. "
            + "Browsers should rely on the HttpOnly access_token cookie instead.")
    private String accessToken;

    @Schema(description = "Raw refresh token — only populated when auth.token-delivery-mode=json. "
            + "Cookie-mode responses never include this; the refresh token is HttpOnly-cookie-only.")
    private String refreshToken;

    // backward-compat constructor used by existing callers that don't yet pass the token
    public LoginResponse(UUID userId, String email, Instant accessTokenExpiresAt) {
        this.userId               = userId;
        this.email                = email;
        this.accessTokenExpiresAt = accessTokenExpiresAt;
    }

    public LoginResponse(UUID userId, String email, Instant accessTokenExpiresAt, String accessToken) {
        this.userId               = userId;
        this.email                = email;
        this.accessTokenExpiresAt = accessTokenExpiresAt;
        this.accessToken          = accessToken;
    }

    public LoginResponse(UUID userId, String email, Instant accessTokenExpiresAt, String accessToken, String refreshToken) {
        this(userId, email, accessTokenExpiresAt, accessToken);
        this.refreshToken = refreshToken;
    }
}
