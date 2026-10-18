package com.company.bsmsvc.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Schema(description = "Create payment intent request")
public record CreatePaymentIntentRequest(
    @NotNull @Schema(description = "Tenant ID") UUID tenantId,
    @NotNull @Schema(description = "Invoice ID to pay") UUID invoiceId
) {}
