package com.example.admsvc.api.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignRoleRequest(@NotNull UUID userId, @NotNull UUID roleId) {
}
