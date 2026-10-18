package com.example.modauth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@Schema(description = "Block or restore sign-in for every person in a brokerage")
public record TenantAccessRequest(
        @NotNull
        @Schema(description = "true = blocked at login and signed out everywhere", example = "true")
        Boolean blocked,

        @NotNull
        @Pattern(regexp = "ACTIVE|SUSPENDED|CANCELLED")
        @Schema(description = "The brokerage status that caused this change", example = "SUSPENDED")
        String status
) {
}
