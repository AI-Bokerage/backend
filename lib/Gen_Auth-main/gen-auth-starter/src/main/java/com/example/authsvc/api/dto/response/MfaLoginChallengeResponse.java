package com.example.authsvc.api.dto.response;

public record MfaLoginChallengeResponse(
        boolean mfaRequired,
        String challengeToken,
        boolean enrollmentRequired
) {}
