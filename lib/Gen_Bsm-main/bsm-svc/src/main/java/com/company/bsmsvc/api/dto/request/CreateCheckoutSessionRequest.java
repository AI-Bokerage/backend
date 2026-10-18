package com.company.bsmsvc.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Schema(description = "Create checkout session request")
public record CreateCheckoutSessionRequest(
    @NotNull @Schema(description = "Tenant ID") UUID tenantId,
    @NotNull @Schema(description = "Invoice ID to pay") UUID invoiceId,
    @NotBlank @Schema(description = "URL to redirect on success") String successUrl,
    @NotBlank @Schema(description = "URL to redirect on cancel") String cancelUrl
) {}
