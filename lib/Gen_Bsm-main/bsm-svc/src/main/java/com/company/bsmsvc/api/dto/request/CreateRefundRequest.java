package com.company.bsmsvc.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

@Schema(description = "Create refund request")
public record CreateRefundRequest(
    @NotNull @Schema(description = "Tenant ID") UUID tenantId,
    @NotNull @Schema(description = "Invoice ID to refund") UUID invoiceId,
    @Positive @Schema(description = "Amount to refund in minor units") long amountMinor,
    @NotBlank @Schema(description = "Reason for the refund") String reason,
    @NotNull @Schema(description = "ID of the user requesting the refund") UUID requestedBy
) {}
