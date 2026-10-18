package com.example.authsvc.domain.model;

import java.util.UUID;

public record MfaChallengeEntry(
        UUID userId,
        boolean enrollmentRequired,
        int attempts
) {}
