package com.example.admsvc.api.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreateRoleRequest(@NotBlank String name, String description) {
}
