package com.example.modauth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "\"Devices I'm signed in on\" — one row per active session")
public record SessionSummaryResponse(
        UUID id,
        String ipAddress,
        String userAgent,
        Instant createdAt,
        Instant lastActivityAt,
        Instant expiresAt,
        @Schema(description = "The session the caller is making this request with")
        boolean isCurrent
) {
}
