package com.example.authsvc.domain.model;

import java.time.Instant;

/**
 * Immutable value object that bundles an issued access/refresh token pair
 * together with their respective expiry instants.
 *
 * <p>Returned by {@code JwtIssuer} after successful token generation and
 * consumed by the API layer to populate HttpOnly response cookies.
 *
 * @param accessToken         Signed RS256 JWT string for the access token.
 * @param refreshToken        Opaque random token (plaintext) for the refresh token.
 *                            The caller is responsible for hashing it before storage.
 * @param accessTokenExpiry   Wall-clock expiry of the access token.
 * @param refreshTokenExpiry  Wall-clock expiry of the refresh token.
 */
public record TokenPair(
        String  accessToken,
        String  refreshToken,
        Instant accessTokenExpiry,
        Instant refreshTokenExpiry
) {}
