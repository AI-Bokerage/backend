package com.example.authsvc.infrastructure.messaging.event;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public record PasswordChangedEvent(
        @JsonProperty("user_id") UUID userId,
        @JsonProperty("session_id") UUID sessionId,
        @JsonProperty("ts") Instant ts
) {}
