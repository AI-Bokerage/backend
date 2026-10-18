package com.example.authsvc.infrastructure.messaging.event;

import java.time.Instant;

/**
 * SHA-256 hash of the email is stored — raw PII must never appear in event payloads.
 */
public record LoginFailedEvent(
        String emailHash,
        String ip,
        String reason,
        Instant timestamp
) {}
