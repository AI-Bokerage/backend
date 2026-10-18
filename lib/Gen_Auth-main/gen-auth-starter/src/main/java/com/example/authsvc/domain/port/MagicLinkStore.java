package com.example.authsvc.domain.port;

import com.example.authsvc.domain.model.MagicLinkEntry;

import java.time.Duration;
import java.util.Optional;

/**
 * Port for storing/retrieving one-time magic-link entries.
 * Backed by Redis — no DB persistence for these tokens.
 */
public interface MagicLinkStore {

    /**
     * Persist a magic-link entry keyed by the SHA-256 hash of the raw token.
     *
     * @param tokenHash SHA-256 hex of the raw token (used as Redis key discriminator)
     * @param entry     the entry to store
     * @param ttl       how long the entry should live
     */
    void save(String tokenHash, MagicLinkEntry entry, Duration ttl);

    /**
     * Look up an entry by the SHA-256 hash of the submitted token.
     */
    Optional<MagicLinkEntry> find(String tokenHash);

    /**
     * Delete the entry immediately (one-time use enforcement).
     */
    void delete(String tokenHash);

    /**
     * Increment the issue-attempt counter for the given key and return the new count.
     * Counter is initialised with the given TTL on first call within the window.
     */
    long incrementRateCounter(String counterKey, Duration window);
}
