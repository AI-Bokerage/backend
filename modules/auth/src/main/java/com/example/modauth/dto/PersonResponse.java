package com.example.modauth.dto;

import com.example.modauth.domain.Role;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "One person (Team Lead or Broker) in the caller's brokerage")
public record PersonResponse(
        UUID userId,
        String name,
        String email,
        Role role,
        UUID teamId,
        String teamName,
        boolean active
) {
}
