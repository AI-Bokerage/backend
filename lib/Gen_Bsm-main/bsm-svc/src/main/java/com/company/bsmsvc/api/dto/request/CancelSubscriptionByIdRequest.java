package com.company.bsmsvc.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Schema(description = "Cancel subscription by id request")
public record CancelSubscriptionByIdRequest(
    @NotNull
    UUID tenantId,
    @NotNull
    Boolean cancelAtPeriodEnd,
    String reason,
    @NotBlank
    String performedBy
) {
}
