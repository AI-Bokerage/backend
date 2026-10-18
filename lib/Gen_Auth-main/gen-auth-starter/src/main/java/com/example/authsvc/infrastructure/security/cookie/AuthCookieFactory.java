package com.example.authsvc.infrastructure.security.cookie;

import com.example.authsvc.config.properties.CookieProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class AuthCookieFactory {

    public static final String ACCESS_TOKEN_COOKIE  = "access_token";
    public static final String REFRESH_TOKEN_COOKIE = "refresh_token";

    /**
     * Extra time the access_token cookie outlives the JWT's own {@code exp} claim.
     *
     * <p>Without this, the cookie's Max-Age equals the token's TTL exactly, so the
     * browser deletes the cookie from its jar at the same instant the JWT expires.
     * middleware.ts's silent-refresh path only triggers when it can verify an
     * access_token and catch a {@code JWTExpired} error — with no grace period the
     * cookie is simply gone by then, the request arrives with no access_token at
     * all, and the user is bounced straight to /login instead of being refreshed.
     */
    private static final Duration ACCESS_TOKEN_COOKIE_GRACE = Duration.ofMinutes(5);

    private final CookieProperties props;

    public ResponseCookie createAccessTokenCookie(String token, Duration maxAge) {
        return buildCookie(ACCESS_TOKEN_COOKIE, token, maxAge.plus(ACCESS_TOKEN_COOKIE_GRACE));
    }

    public ResponseCookie createRefreshTokenCookie(String token, Duration maxAge) {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, token)
                .httpOnly(props.isHttpOnly())
                .secure(props.isSecure())
                .sameSite(props.getSameSite())
                .path("/api/v1/auth")
                .domain(props.getDomain())
                .maxAge(maxAge)
                .build();
    }

    public ResponseCookie clearAccessTokenCookie() {
        return clearCookie(ACCESS_TOKEN_COOKIE);
    }

    public ResponseCookie clearRefreshTokenCookie() {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, "")
                .httpOnly(true)
                .secure(props.isSecure())
                .sameSite(props.getSameSite())
                .path("/api/v1/auth")
                .domain(props.getDomain())
                .maxAge(Duration.ZERO)
                .build();
    }

    /**
     * Clears any legacy {@code refresh_token} cookie that may have been set with
     * {@code Path=/} by an older version of the code. Sending this alongside every
     * login/refresh/logout response removes the duplicate-cookie ambiguity in browsers.
     */
    public ResponseCookie clearLegacyRefreshTokenCookie() {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, "")
                .httpOnly(true)
                .secure(props.isSecure())
                .sameSite(props.getSameSite())
                .path(props.getPath())   // "/"  — matches the old accidental root-path cookie
                .domain(props.getDomain())
                .maxAge(Duration.ZERO)
                .build();
    }

    /**
     * Clears the {@code refresh_token} cookie that was previously scoped to
     * {@code Path=/api/v1/auth/refresh}. Required during login/refresh so that old
     * cookies from a prior code version do not shadow the new token at
     * {@code Path=/api/v1/auth} and trigger false replay-attack detection.
     */
    public ResponseCookie clearOldNarrowRefreshTokenCookie() {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, "")
                .httpOnly(true)
                .secure(props.isSecure())
                .sameSite(props.getSameSite())
                .path("/api/v1/auth/refresh")
                .domain(props.getDomain())
                .maxAge(Duration.ZERO)
                .build();
    }

    private ResponseCookie buildCookie(String name, String value, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(props.isHttpOnly())
                .secure(props.isSecure())
                .sameSite(props.getSameSite())
                .path(props.getPath())
                .domain(props.getDomain())
                .maxAge(maxAge)
                .build();
    }

    private ResponseCookie clearCookie(String name) {
        return ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(props.isSecure())
                .sameSite(props.getSameSite())
                .path(props.getPath())
                .domain(props.getDomain())
                .maxAge(Duration.ZERO)
                .build();
    }
}
