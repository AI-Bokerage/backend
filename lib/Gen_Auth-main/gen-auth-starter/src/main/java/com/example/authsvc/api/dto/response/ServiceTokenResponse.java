package com.example.authsvc.api.dto.response;

/**
 * Response from {@code POST /internal/service-token}.
 *
 * @param accessToken the signed JWT with {@code user_type=SUPER_ADMIN}, {@code sub=TenantConstants.SERVICE_ACCOUNT_ID}
 * @param expiresIn   token lifetime in seconds
 */
public record ServiceTokenResponse(
        String accessToken,
        int    expiresIn
) {}
