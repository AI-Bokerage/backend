package com.example.authsvc.api.dto.response;

public record MfaChallengeInfo(
        String challengeToken,
        boolean enrollmentRequired
) {}
