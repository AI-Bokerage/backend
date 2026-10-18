package com.company.bsmsvc.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Schema(description = "Pause subscription request")
public record PauseSubscriptionRequest(
    @NotNull
    UUID tenantId,
    @NotBlank
    String performedBy,
    String reason
) {
}
