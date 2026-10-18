package com.example.authsvc.domain.port;

import com.example.authsvc.domain.model.OAuthSignupChallengeEntry;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

public interface OAuthSignupChallengeStore {

    /** Generates a fresh opaque token, stores the entry, and returns the raw token. */
    String issue(UUID userId, Duration ttl);

    Optional<OAuthSignupChallengeEntry> find(String token);

    void delete(String token);
}
