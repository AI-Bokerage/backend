package com.example.authsvc.domain.port;

import com.example.authsvc.domain.model.MfaChallengeEntry;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

public interface MfaChallengeStore {

    /** Generates a fresh opaque token, stores the entry, and returns the raw token. */
    String issue(UUID userId, boolean enrollmentRequired, Duration ttl);

    Optional<MfaChallengeEntry> find(String token);

    void incrementAttempts(String token);

    void delete(String token);
}
