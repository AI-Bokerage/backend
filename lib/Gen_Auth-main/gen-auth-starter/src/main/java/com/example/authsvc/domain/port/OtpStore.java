package com.example.authsvc.domain.port;

import java.time.Duration;
import java.util.UUID;

public interface OtpStore {

    /** Generates a fresh id, stores the given code hash under it with the given TTL, and returns the id. */
    UUID issue(String codeHash, Duration ttl);

    /**
     * Atomically checks whether the stored hash for {@code otpId} matches
     * {@code codeHash}, then deletes the entry regardless of outcome —
     * always single-use, no replay even on a wrong-code attempt.
     */
    boolean verifyAndConsume(UUID otpId, String codeHash);
}
