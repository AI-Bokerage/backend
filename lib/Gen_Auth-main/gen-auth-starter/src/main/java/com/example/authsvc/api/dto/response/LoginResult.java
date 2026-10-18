package com.example.authsvc.api.dto.response;

import java.time.Duration;

public record LoginResult(
        LoginResponse response,
        String accessToken,
        String refreshToken,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        MfaChallengeInfo mfaChallenge
) {
    /** Preserves every existing call site that predates MFA — no challenge, real tokens only. */
    public LoginResult(LoginResponse response, String accessToken, String refreshToken,
                       Duration accessTokenTtl, Duration refreshTokenTtl) {
        this(response, accessToken, refreshToken, accessTokenTtl, refreshTokenTtl, null);
    }

    public static LoginResult challenge(MfaChallengeInfo mfaChallenge) {
        return new LoginResult(null, null, null, null, null, mfaChallenge);
    }
}
