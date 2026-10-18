package com.example.modauth.dto;

import com.example.modauth.domain.InvitationStatus;
import com.example.modauth.domain.Role;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "An invitation's current state — PENDING until accepted, then ACCEPTED")
public record InvitationResponse(
        UUID id,
        String name,
        String email,
        Role role,
        String teamName,
        UUID teamId,
        InvitationStatus status,
        Instant expiresAt
) {
}
