package com.example.admsvc.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record InitiateOffboardingRequest(@NotNull UUID userId, @NotBlank String reason) {
}
